# =============================================
# Generic Android build/run workflow — no Android Studio required.
# Nothing here is project-specific: the app module, application id, launcher
# activity and ABI are all detected from the project it runs in.
#
# Canonical copy: ~/AndroidStudioProjects/android.mk
# Run from any project root:  make -f ~/AndroidStudioProjects/android.mk refresh
# (the `make` shell function in ~/.bashrc does this for you)
# =============================================

# One per line: `A := B := C` would assign A the literal text "B := C".
ifdef NO_COLOR
    GREEN :=
    YELLOW :=
    RED :=
    BLUE :=
    CYAN :=
    DIM :=
    BOLD :=
    NC :=
else
    GREEN := \033[0;32m
    YELLOW := \033[1;33m
    RED := \033[0;31m
    BLUE := \033[0;34m
    CYAN := \033[0;36m
    DIM := \033[2m
    BOLD := \033[1m
    NC := \033[0m
endif

# Build flags come from each project's gradle.properties. Nothing is overridden
# here: user-home gradle.properties wins over the project one anyway, and on this
# box its -XX:MaxMetaspaceSize=1g matters — 512m GC-thrashes release builds.
GRADLE := ./gradlew
ADB := adb

# --- Project detection ----------------------------------------------------
# Immediate (:=) so each is resolved once per invocation; these are local file
# reads, unlike the adb calls below.

# The application module is the one declaring an applicationId; library modules
# never do. Falls back to "app".
APP_MODULE := $(shell find . -maxdepth 2 -name 'build.gradle*' -not -path './build/*' \
    -exec grep -l 'applicationId' {} + 2>/dev/null | head -1 | xargs -r dirname | sed 's|^\./||')
APP_MODULE := $(if $(APP_MODULE),$(APP_MODULE),app)
APP_BUILD_FILE := $(firstword $(wildcard $(APP_MODULE)/build.gradle.kts $(APP_MODULE)/build.gradle))
MANIFEST := $(APP_MODULE)/src/main/AndroidManifest.xml

# Kotlin DSL uses `namespace = "x"`, Groovy `namespace "x"`.
NAMESPACE := $(shell grep -hoE '^[[:space:]]*namespace[[:space:]]*=?[[:space:]]*"[^"]+"' $(APP_BUILD_FILE) 2>/dev/null \
    | head -1 | grep -oE '"[^"]+"' | tr -d '"')

# AGP writes the fully-resolved id (suffixes, flavors and all) next to the APK,
# so prefer it. The gradle read is only for a tree that has never been built.
DEBUG_META := $(APP_MODULE)/build/outputs/apk/debug/output-metadata.json
APP_ID := $(shell \
    if [ -f "$(DEBUG_META)" ]; then \
        grep -oE '"applicationId"[[:space:]]*:[[:space:]]*"[^"]+"' "$(DEBUG_META)" \
            | head -1 | grep -oE '"[^"]+"$$' | tr -d '"'; \
    elif [ -n "$(APP_BUILD_FILE)" ]; then \
        base=$$(grep -hoE 'applicationId[[:space:]]*=?[[:space:]]*"[^"]+"' "$(APP_BUILD_FILE)" \
            | head -1 | grep -oE '"[^"]+"' | tr -d '"'); \
        sfx=$$(awk '/^[[:space:]]*debug[[:space:]]*\{/{d=1} d&&/applicationIdSuffix/{print;exit} d&&/^[[:space:]]*\}[[:space:]]*$$/{d=0}' \
            "$(APP_BUILD_FILE)" | grep -oE '"[^"]+"' | tr -d '"'); \
        echo "$$base$$sfx"; \
    fi)

# The SOURCE manifest, deliberately: the merged one also carries LAUNCHER
# activities from libraries (LeakCanary registers one in debug), and those win
# over the real app if you launch by category instead of by component.
LAUNCH_CLASS := $(shell [ -f "$(MANIFEST)" ] && awk '\
    /<activity/ {inact=1; name=""; main=0; lau=0} \
    inact && name=="" && match($$0, /android:name="[^"]+"/) {name=substr($$0, RSTART+14, RLENGTH-15)} \
    inact && /android.intent.action.MAIN/ {main=1} \
    inact && /android.intent.category.LAUNCHER/ {lau=1} \
    /<\/activity>/ {if (inact && main && lau && name!="") {print name; exit} inact=0} \
    ' "$(MANIFEST)")
# A leading-dot class is relative to the namespace, NOT to the application id —
# they differ whenever a debug build sets applicationIdSuffix.
LAUNCH_ACTIVITY := $(APP_ID)/$(if $(filter .%,$(LAUNCH_CLASS)),$(NAMESPACE)$(LAUNCH_CLASS),$(LAUNCH_CLASS))

# Only pass the ABI properties to projects that actually read them; an unused
# -P is harmless but needlessly perturbs the configuration cache.
HAS_DEBUG_ABI := $(shell grep -rlq 'debugAbi' --include='*.gradle.kts' --include='*.gradle' --include='gradle.properties' . 2>/dev/null && echo 1)
HAS_RELEASE_ABI := $(shell grep -rlq 'releaseAbi' --include='*.gradle.kts' --include='*.gradle' --include='gradle.properties' . 2>/dev/null && echo 1)

# Lazily expanded: only costs an adb call on targets that use it. Blank with no
# device, which leaves the project's own default in charge.
DEVICE_ABI = $(shell $(ADB) shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r')
DEBUG_ABI = $(if $(HAS_DEBUG_ABI),-PdebugAbi=$(DEVICE_ABI),)
RELEASE_ABI_ONE = $(if $(HAS_RELEASE_ABI),-PreleaseAbi=$(DEVICE_ABI),)
RELEASE_ABI_ALL = $(if $(HAS_RELEASE_ABI),-PreleaseAbi=all,)

APK_DEBUG_DIR := $(APP_MODULE)/build/outputs/apk/debug
APK_RELEASE_DIR := $(APP_MODULE)/build/outputs/apk/release
AAB_DIR := $(APP_MODULE)/build/outputs/bundle/release
APKSIGNER = $(shell ls $(HOME)/Android/Sdk/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)

PROJECT := $(notdir $(CURDIR))

default: help

.PHONY: help
help:
	@printf "$(BLUE)=============================================$(NC)\n"
	@printf "$(BOLD)$(BLUE) $(PROJECT)$(NC) $(DIM)— no-Studio build workflow$(NC)\n"
	@printf "$(BLUE)=============================================$(NC)\n\n"
	@printf "$(BOLD)Every day$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)refresh$(NC)      Rebuild changed code, install, relaunch $(DIM)(the main loop)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)all$(NC)          Alias for refresh\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)restart$(NC)      Relaunch on device, no build\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)logs$(NC)         Live logcat for this app only\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)logs-error$(NC)   Errors and crashes only\n\n"
	@printf "$(BOLD)Building$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)assemble$(NC)     Debug APK, no install\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)release$(NC)      $(BOLD)Signed$(NC) APK for this device's ABI $(DIM)(fast)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)release-all$(NC)  $(BOLD)Signed$(NC) APKs, every ABI $(DIM)(for publishing)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)bundle$(NC)       $(BOLD)Signed$(NC) .aab for Play\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)rebuild$(NC)      Clean + install + launch $(DIM)(cache gone weird)$(NC)\n\n"
	@printf "$(BOLD)Install$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)install$(NC)      Build + install debug\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)reinstall$(NC)    Uninstall first $(DIM)(fixes signature conflicts)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)install-release$(NC)  Install the signed APK\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)uninstall$(NC)    Remove this build\n\n"
	@printf "$(BOLD)Tests$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)test$(NC)         Unit tests $(DIM)(no device)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)test-device$(NC)  Instrumented tests $(DIM)(T=SomeTest to filter)$(NC)\n"
	@printf "               $(DIM)leaves the app uninstalled — run 'make refresh' after$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)lint$(NC)         Android lint\n\n"
	@printf "$(BOLD)RAM / cache$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)kill$(NC)         Stop Gradle+Kotlin daemons $(DIM)(frees several GB)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)ram$(NC)          What is eating memory right now\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)clean$(NC)        Delete build outputs\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)clean-cache$(NC)  Drop the configuration cache only\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)devices$(NC)      List connected devices\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)info$(NC)         What this Makefile detected here\n\n"
	@printf "$(DIM)Tip: close Android Studio first — it holds its own Gradle daemon.$(NC)\n"
	@printf "$(BLUE)=============================================$(NC)\n"

# Detection is invisible until it is wrong; this makes it inspectable.
.PHONY: info
info:
	@printf "$(BOLD)Project$(NC)          %s\n" "$(PROJECT)"
	@printf "$(BOLD)App module$(NC)       %s\n" "$(APP_MODULE)"
	@printf "$(BOLD)Namespace$(NC)        %s\n" "$(NAMESPACE)"
	@printf "$(BOLD)Application id$(NC)   %s %s\n" "$(APP_ID)" \
		"$$([ -f '$(DEBUG_META)' ] && printf '$(DIM)(from AGP metadata)$(NC)' || printf '$(DIM)(from build.gradle — not built yet)$(NC)')"
	@printf "$(BOLD)Launch$(NC)           %s\n" "$(LAUNCH_ACTIVITY)"
	@printf "$(BOLD)Device ABI$(NC)       %s\n" "$(DEVICE_ABI)"
	@$(if $(HAS_DEBUG_ABI)$(HAS_RELEASE_ABI),\
		printf "$(BOLD)ABI flags$(NC)        %s\n" "$(strip $(DEBUG_ABI) $(RELEASE_ABI_ONE))",\
		printf "$(BOLD)ABI flags$(NC)        $(DIM)project declares no debugAbi/releaseAbi$(NC)\n")

# --- Guards ---------------------------------------------------------------

.PHONY: check-project
check-project:
	@if [ ! -x "$(GRADLE)" ]; then \
		printf "$(RED)No ./gradlew here — run this from an Android project root.$(NC)\n"; exit 1; fi
	@if [ -z "$(APP_ID)" ]; then \
		printf "$(RED)Could not work out the application id.$(NC)\n"; \
		printf "  -> Looked for applicationId in $(APP_BUILD_FILE)\n"; \
		printf "  -> Override it: $(BOLD)make refresh APP_ID=com.example.app$(NC)\n"; exit 1; fi
	@if [ -z "$(LAUNCH_CLASS)" ]; then \
		printf "$(RED)No MAIN/LAUNCHER activity found in $(MANIFEST).$(NC)\n"; \
		printf "  -> Override it: $(BOLD)make refresh LAUNCH_ACTIVITY=pkg/pkg.MainActivity$(NC)\n"; exit 1; fi

.PHONY: check-adb
check-adb:
	@devices=$$($(ADB) devices 2>/dev/null | tr -d '\r' | sed '1d' | awk '$$2 == "device" {print $$1}'); \
	if [ -z "$$devices" ]; then \
		printf "$(YELLOW)No device — restarting adb...$(NC)\n"; \
		$(ADB) kill-server >/dev/null 2>&1; $(ADB) start-server >/dev/null 2>&1; sleep 2; \
		devices=$$($(ADB) devices 2>/dev/null | tr -d '\r' | sed '1d' | awk '$$2 == "device" {print $$1}'); \
		if [ -z "$$devices" ]; then \
			printf "$(RED)No Android device connected.$(NC)\n"; \
			printf "  -> Plug in the phone and enable $(BOLD)USB debugging$(NC).\n"; \
			exit 1; \
		fi; \
	fi

# Fails in a second rather than after a long build. Only projects that keep
# signing in local.properties can be checked this way; for anything else the
# apksigner verify after the build is the real guarantee.
.PHONY: check-signing
check-signing:
	@if grep -q '^storeFile=' local.properties 2>/dev/null; then \
		missing=""; \
		for k in storeFile storePassword keyAlias keyPassword; do \
			grep -q "^$$k=" local.properties || missing="$$missing $$k"; \
		done; \
		if [ -n "$$missing" ]; then \
			printf "$(RED)Signing incomplete in local.properties:$(NC)$$missing\n"; exit 1; fi; \
		store=$$(grep '^storeFile=' local.properties | cut -d= -f2-); \
		if [ ! -f "$$store" ] && [ ! -f "$(APP_MODULE)/$$store" ]; then \
			printf "$(RED)Keystore not found: $$store$(NC)\n"; exit 1; fi; \
	fi

# --- The main loop --------------------------------------------------------

.PHONY: refresh
refresh: check-project check-adb
	@printf "$(YELLOW)$(PROJECT): building + installing$(if $(DEBUG_ABI), (ABI: $(DEVICE_ABI)))...$(NC)\n"
	@$(GRADLE) :$(APP_MODULE):installDebug $(DEBUG_ABI) || { \
		printf "$(RED)Install failed.$(NC)\n"; \
		printf "  -> A different signature, or a newer build already on the device\n"; \
		printf "     (VERSION_DOWNGRADE), both need it gone first: $(BOLD)make reinstall$(NC)\n"; \
		printf "  -> Currently installed: %s\n" \
			"$$($(ADB) shell dumpsys package $(APP_ID) 2>/dev/null | grep -m1 versionCode | tr -d '\r' | sed 's/^ *//' || echo 'not installed')"; \
		exit 1; \
	}
	@$(MAKE) --no-print-directory -f $(firstword $(MAKEFILE_LIST)) restart

.PHONY: all
all: refresh

.PHONY: run
run: check-project check-adb
	@$(ADB) shell am start -n $(LAUNCH_ACTIVITY) >/dev/null 2>&1 \
		&& printf "$(GREEN)Launched$(NC) $(DIM)$(LAUNCH_ACTIVITY)$(NC)\n" \
		|| printf "$(RED)Launch failed — installed? (make install)$(NC)\n"

.PHONY: stop
stop: check-adb
	@$(ADB) shell am force-stop $(APP_ID)

.PHONY: restart
restart: stop run

# --- Building -------------------------------------------------------------

.PHONY: assemble
assemble: check-project
	@printf "$(YELLOW)Assembling debug APK...$(NC)\n"
	@$(GRADLE) :$(APP_MODULE):assembleDebug $(DEBUG_ABI)
	@printf "$(GREEN)APK:$(NC) $$(ls -1 $(APK_DEBUG_DIR)/*.apk 2>/dev/null | head -1)\n"

.PHONY: release
release: check-project check-signing
	@printf "$(YELLOW)Building signed release$(if $(RELEASE_ABI_ONE), ($(DEVICE_ABI) only))...$(NC)\n"
	@$(GRADLE) :$(APP_MODULE):assembleRelease $(RELEASE_ABI_ONE)
	@$(MAKE) --no-print-directory -f $(firstword $(MAKEFILE_LIST)) verify-signature

.PHONY: release-all
release-all: check-project check-signing
	@printf "$(YELLOW)Building signed release (all ABIs)...$(NC)\n"
	@$(GRADLE) :$(APP_MODULE):assembleRelease $(RELEASE_ABI_ALL)
	@$(MAKE) --no-print-directory -f $(firstword $(MAKEFILE_LIST)) verify-signature

.PHONY: bundle
bundle: check-project check-signing
	@printf "$(YELLOW)Building signed App Bundle...$(NC)\n"
	@$(GRADLE) :$(APP_MODULE):bundleRelease
	@printf "$(GREEN)AAB:$(NC) $$(ls -1 $(AAB_DIR)/*.aab 2>/dev/null | head -1)\n"

# A "signed APK" target that never checks the signature is how unsigned builds ship.
.PHONY: verify-signature
verify-signature:
	@found=0; \
	for apk in $$(ls -1 $(APK_RELEASE_DIR)/*.apk 2>/dev/null); do \
		found=1; size=$$(du -h "$$apk" | cut -f1); \
		if [ -n "$(APKSIGNER)" ]; then \
			if $(APKSIGNER) verify "$$apk" >/dev/null 2>&1; then \
				printf "$(GREEN)signed$(NC)  %s  $(DIM)(%s)$(NC)\n" "$$apk" "$$size"; \
			else \
				printf "$(RED)UNSIGNED$(NC) %s\n" "$$apk"; exit 1; \
			fi; \
		else \
			printf "$(YELLOW)? $(NC)%s $(DIM)(apksigner not found, unverified)$(NC)\n" "$$apk"; \
		fi; \
	done; \
	[ "$$found" = 1 ] || printf "$(RED)No release APK was produced.$(NC)\n"

.PHONY: rebuild
rebuild: clean-all refresh

# --- Install --------------------------------------------------------------

.PHONY: install
install: check-project check-adb
	@$(GRADLE) :$(APP_MODULE):installDebug $(DEBUG_ABI)

.PHONY: uninstall
uninstall: check-adb
	-@$(ADB) uninstall $(APP_ID) 2>/dev/null || true

.PHONY: reinstall
reinstall: uninstall install run

.PHONY: install-release
install-release: check-adb
	@apk=$$(ls -1 $(APK_RELEASE_DIR)/*.apk 2>/dev/null | grep -v universal | head -1); \
	if [ -z "$$apk" ]; then printf "$(RED)No release APK — run: make release$(NC)\n"; exit 1; fi; \
	printf "$(YELLOW)Installing $$apk$(NC)\n"; \
	$(ADB) install -r "$$apk"

# --- Tests ----------------------------------------------------------------

.PHONY: test
test: check-project
	@$(GRADLE) :$(APP_MODULE):testDebugUnitTest

# connectedAndroidTest rejects --tests; the runner argument is the supported
# filter. AGP uninstalls the app when it finishes — `make refresh` puts it back.
.PHONY: test-device
test-device: check-project check-adb
	@if [ -n "$(T)" ]; then \
		printf "$(YELLOW)Instrumented tests: $(T)$(NC)\n"; \
		$(GRADLE) :$(APP_MODULE):connectedDebugAndroidTest $(DEBUG_ABI) \
			-Pandroid.testInstrumentationRunnerArguments.class=$(T); \
	else \
		printf "$(YELLOW)All instrumented tests (T=Class to filter)$(NC)\n"; \
		$(GRADLE) :$(APP_MODULE):connectedDebugAndroidTest $(DEBUG_ABI); \
	fi

.PHONY: lint
lint: check-project
	@$(GRADLE) :$(APP_MODULE):lintDebug

# --- Logs -----------------------------------------------------------------

.PHONY: logs
logs: check-adb
	@pid=$$($(ADB) shell pidof $(APP_ID) | tr -d '\r'); \
	if [ -z "$$pid" ]; then \
		printf "$(RED)$(APP_ID) is not running — start it with: make run$(NC)\n"; exit 1; \
	fi; \
	printf "$(CYAN)pid $$pid — Ctrl+C to stop$(NC)\n"; \
	$(ADB) logcat --pid=$$pid

.PHONY: logs-error
logs-error: check-adb
	@pid=$$($(ADB) shell pidof $(APP_ID) | tr -d '\r'); \
	if [ -n "$$pid" ]; then $(ADB) logcat *:E --pid=$$pid; \
	else $(ADB) logcat *:E | grep -E "$(APP_ID)|AndroidRuntime"; fi

.PHONY: crash
crash: check-adb
	@$(ADB) logcat -d -b crash | grep -A 30 -iE "$(APP_ID)" | tail -40 \
		|| printf "$(GREEN)No crashes logged.$(NC)\n"

# --- RAM / cache ----------------------------------------------------------

.PHONY: kill
kill:
	@printf "$(YELLOW)Stopping Gradle + Kotlin daemons...$(NC)\n"
	@before=$$(free -m | awk '/^Mem:/ {print $$7}'); \
	[ -x "$(GRADLE)" ] && $(GRADLE) --stop >/dev/null 2>&1 || true; \
	pkill -f "[K]otlinCompileDaemon" 2>/dev/null || true; \
	sleep 2; \
	after=$$(free -m | awk '/^Mem:/ {print $$7}'); \
	printf "$(GREEN)Freed %s MB.$(NC) Available: %s MB\n" "$$((after - before))" "$$after"

.PHONY: ram
ram:
	@free -h | awk '/^Mem:/ {printf "$(BOLD)Memory:$(NC) %s available of %s\n\n", $$7, $$2}'
	@printf "$(BOLD)%8s  %-7s %s$(NC)\n" "RSS" "PID" "PROCESS"
	@ps -eo rss,pid,comm --sort=-rss | grep -iE "studio|java|kotlin|gradle" | grep -v grep \
		| awk '$$1 > 204800 {printf "%7.1fG  %-7s %s\n", $$1/1048576, $$2, $$3}' | head -8
	@pgrep -f "studio.sh|android-studio" >/dev/null 2>&1 \
		&& printf "\n$(YELLOW)Android Studio is running — closing it frees the most.$(NC)\n" || true

.PHONY: clean
clean: check-project
	@$(GRADLE) clean

.PHONY: clean-cache
clean-cache:
	@rm -rf .gradle/configuration-cache
	@printf "$(GREEN)Configuration cache dropped.$(NC)\n"

.PHONY: clean-all
clean-all: clean clean-cache

.PHONY: devices
devices:
	@$(ADB) devices -l
