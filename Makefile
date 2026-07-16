# =============================================
# Scanly — terminal-only build/run workflow
# No Android Studio required. `make help` for the list.
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

# Build flags live in gradle.properties (config cache, caching, workers, heap).
# Nothing is overridden here on purpose: the user-home gradle.properties wins over
# the project one, and its -XX:MaxMetaspaceSize=1g is required — 512m GC-thrashes
# release builds.
GRADLE := ./gradlew
ADB := adb

PACKAGE := com.skeler.scanely
# debug carries applicationIdSuffix ".debug" and installs alongside the real app,
# so the launch id must be the suffixed one — the class name keeps the base package.
PACKAGE_DEBUG := $(PACKAGE).debug
# Explicit component: LeakCanary also registers a LAUNCHER activity in debug, so
# `monkey -c android.intent.category.LAUNCHER` starts the wrong app.
MAIN_ACTIVITY := $(PACKAGE_DEBUG)/$(PACKAGE).MainActivity

# Lazily expanded: only costs an adb call on targets that use it.
# Blank when no device — the build then falls back to its arm64-v8a default.
DEVICE_ABI = $(shell $(ADB) shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r')
DEBUG_ABI = -PdebugAbi=$(DEVICE_ABI)

APK_DEBUG_DIR := app/build/outputs/apk/debug
APK_RELEASE_DIR := app/build/outputs/apk/release
APKSIGNER = $(shell ls $(HOME)/Android/Sdk/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)

default: help

.PHONY: help
help:
	@printf "$(BLUE)=============================================$(NC)\n"
	@printf "$(BOLD)$(BLUE) Scanly — no-Studio build workflow$(NC)\n"
	@printf "$(BLUE)=============================================$(NC)\n\n"
	@printf "$(BOLD)Every day$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)refresh$(NC)      Rebuild changed code, install, relaunch $(DIM)(the main loop, ~6s warm)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)all$(NC)          Alias for refresh\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)restart$(NC)      Relaunch on device, no build\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)logs$(NC)         Live logcat for the app only\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)logs-error$(NC)   Errors and crashes only\n\n"
	@printf "$(BOLD)Building$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)assemble$(NC)     Debug APK, no install $(DIM)($(APK_DEBUG_DIR))$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)release$(NC)      $(BOLD)Signed$(NC) APK, arm64 only $(DIM)(fast — for your phone)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)release-all$(NC)  $(BOLD)Signed$(NC) APKs, every ABI $(DIM)(slow — for publishing)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)bundle$(NC)       $(BOLD)Signed$(NC) .aab for Play $(DIM)(slow)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)rebuild$(NC)      Clean + assemble + install + launch $(DIM)(cache gone weird)$(NC)\n\n"
	@printf "$(BOLD)Install$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)install$(NC)      Build + install debug\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)reinstall$(NC)    Uninstall first $(DIM)(fixes signature conflicts)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)install-release$(NC)  Install the signed APK\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)uninstall$(NC)    Remove debug build\n\n"
	@printf "$(BOLD)Tests$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)test$(NC)         Unit tests $(DIM)(no device)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)test-device$(NC)  Instrumented tests $(DIM)(T=SomeTest to filter)$(NC)\n"
	@printf "               $(DIM)leaves the app uninstalled — run 'make refresh' after$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)lint$(NC)         Android lint\n\n"
	@printf "$(BOLD)RAM / cache$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)kill$(NC)         Stop Gradle+Kotlin daemons $(DIM)(frees ~4 GB)$(NC)\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)ram$(NC)          What is eating memory right now\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)clean$(NC)        Delete build outputs\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)clean-cache$(NC)  Drop the configuration cache only\n"
	@printf "  $(GREEN)make$(NC) $(BOLD)devices$(NC)      List connected devices\n\n"
	@printf "$(DIM)Tip: close Android Studio first — it holds its own Gradle daemon.$(NC)\n"
	@printf "$(DIM)     'make kill' after a session returns the RAM.$(NC)\n"
	@printf "$(BLUE)=============================================$(NC)\n"

# --- Guards ---------------------------------------------------------------

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
			printf "  -> Check the 'Allow USB debugging?' prompt on screen.\n"; \
			exit 1; \
		fi; \
	fi

# Fails before a long build rather than after it.
.PHONY: check-signing
check-signing:
	@missing=""; \
	for k in storeFile storePassword keyAlias keyPassword; do \
		grep -q "^$$k=" local.properties 2>/dev/null || missing="$$missing $$k"; \
	done; \
	if [ -n "$$missing" ]; then \
		printf "$(RED)Signing not configured — missing in local.properties:$(NC)$$missing\n"; \
		printf "  -> A release build would come out unsigned.\n"; \
		exit 1; \
	fi; \
	store=$$(grep "^storeFile=" local.properties | cut -d= -f2-); \
	if [ ! -f "app/$$store" ] && [ ! -f "$$store" ]; then \
		printf "$(RED)Keystore not found: $$store$(NC)\n"; exit 1; \
	fi

# --- The main loop --------------------------------------------------------

# Gradle already skips work when nothing changed, so this is the fast path.
.PHONY: refresh
refresh: check-adb
	@printf "$(YELLOW)Building + installing (ABI: $(DEVICE_ABI))...$(NC)\n"
	@$(GRADLE) :app:installDebug $(DEBUG_ABI) || { \
		printf "$(RED)Install failed.$(NC)\n"; \
		printf "  -> Signature conflict? $(BOLD)make reinstall$(NC)\n"; \
		exit 1; \
	}
	@$(MAKE) --no-print-directory restart

# Alias: installDebug already assembles, so there is nothing for `all` to add.
.PHONY: all
all: refresh

.PHONY: run
run: check-adb
	@$(ADB) shell am start -n $(MAIN_ACTIVITY) >/dev/null 2>&1 \
		&& printf "$(GREEN)Launched.$(NC)\n" \
		|| printf "$(RED)Launch failed — is it installed? (make install)$(NC)\n"

.PHONY: stop
stop: check-adb
	@$(ADB) shell am force-stop $(PACKAGE_DEBUG)

.PHONY: restart
restart: stop run

# --- Building -------------------------------------------------------------

.PHONY: assemble
assemble:
	@printf "$(YELLOW)Assembling debug APK...$(NC)\n"
	@$(GRADLE) :app:assembleDebug $(DEBUG_ABI)
	@printf "$(GREEN)APK:$(NC) $$(ls -1 $(APK_DEBUG_DIR)/*.apk 2>/dev/null | head -1)\n"

# releaseAbi=arm64-v8a is already set in gradle.properties; the universal APK is
# ~250 MB of ONNX assets, so building all four is the slow tail of a release.
.PHONY: release
release: check-signing
	@printf "$(YELLOW)Building signed release (arm64-v8a only)...$(NC)\n"
	@$(GRADLE) :app:assembleRelease -PreleaseAbi=arm64-v8a
	@$(MAKE) --no-print-directory verify-signature

.PHONY: release-all
release-all: check-signing
	@printf "$(YELLOW)Building signed release (all ABIs — slow)...$(NC)\n"
	@$(GRADLE) :app:assembleRelease -PreleaseAbi=all
	@$(MAKE) --no-print-directory verify-signature

.PHONY: bundle
bundle: check-signing
	@printf "$(YELLOW)Building signed App Bundle...$(NC)\n"
	@$(GRADLE) :app:bundleRelease
	@printf "$(GREEN)AAB:$(NC) $$(ls -1 app/build/outputs/bundle/release/*.aab 2>/dev/null | head -1)\n"

# A "signed APK" target that never checks the signature is how unsigned builds ship.
.PHONY: verify-signature
verify-signature:
	@for apk in $$(ls -1 $(APK_RELEASE_DIR)/*.apk 2>/dev/null); do \
		size=$$(du -h "$$apk" | cut -f1); \
		if [ -n "$(APKSIGNER)" ]; then \
			if $(APKSIGNER) verify --print-certs "$$apk" >/dev/null 2>&1; then \
				printf "$(GREEN)signed$(NC)  %s  $(DIM)(%s)$(NC)\n" "$$apk" "$$size"; \
			else \
				printf "$(RED)UNSIGNED$(NC) %s\n" "$$apk"; exit 1; \
			fi; \
		else \
			printf "$(YELLOW)? $(NC)%s $(DIM)(apksigner not found, not verified)$(NC)\n" "$$apk"; \
		fi; \
	done

.PHONY: rebuild
rebuild: clean-all refresh

# --- Install --------------------------------------------------------------

.PHONY: install
install: check-adb
	@$(GRADLE) :app:installDebug $(DEBUG_ABI)

.PHONY: uninstall
uninstall: check-adb
	-@$(ADB) uninstall $(PACKAGE_DEBUG) 2>/dev/null || true

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
test:
	@$(GRADLE) :app:testDebugUnitTest

# connectedAndroidTest rejects --tests; the runner argument is the supported filter.
# AGP uninstalls the app when it finishes — `make refresh` puts it back.
.PHONY: test-device
test-device: check-adb
	@if [ -n "$(T)" ]; then \
		printf "$(YELLOW)Instrumented tests: $(T)$(NC)\n"; \
		$(GRADLE) :app:connectedDebugAndroidTest $(DEBUG_ABI) \
			-Pandroid.testInstrumentationRunnerArguments.class=$(T); \
	else \
		printf "$(YELLOW)All instrumented tests (T=Class to filter)$(NC)\n"; \
		$(GRADLE) :app:connectedDebugAndroidTest $(DEBUG_ABI); \
	fi

.PHONY: lint
lint:
	@$(GRADLE) :app:lintDebug

# --- Logs -----------------------------------------------------------------

.PHONY: logs
logs: check-adb
	@pid=$$($(ADB) shell pidof $(PACKAGE_DEBUG) | tr -d '\r'); \
	if [ -z "$$pid" ]; then \
		printf "$(RED)Not running — start it with: make run$(NC)\n"; exit 1; \
	fi; \
	printf "$(CYAN)pid $$pid — Ctrl+C to stop$(NC)\n"; \
	$(ADB) logcat --pid=$$pid

.PHONY: logs-error
logs-error: check-adb
	@pid=$$($(ADB) shell pidof $(PACKAGE_DEBUG) | tr -d '\r'); \
	if [ -n "$$pid" ]; then $(ADB) logcat *:E --pid=$$pid; \
	else $(ADB) logcat *:E | grep -E "$(PACKAGE)|AndroidRuntime"; fi

.PHONY: crash
crash: check-adb
	@$(ADB) logcat -d -b crash | grep -A 30 -iE "$(PACKAGE)" | tail -40 \
		|| printf "$(GREEN)No crashes logged.$(NC)\n"

# --- RAM / cache ----------------------------------------------------------

.PHONY: kill
kill:
	@printf "$(YELLOW)Stopping Gradle + Kotlin daemons...$(NC)\n"
	@before=$$(free -m | awk '/^Mem:/ {print $$7}'); \
	$(GRADLE) --stop >/dev/null 2>&1 || true; \
	pkill -f "[K]otlinCompileDaemon" 2>/dev/null || true; \
	sleep 2; \
	after=$$(free -m | awk '/^Mem:/ {print $$7}'); \
	freed=$$((after - before)); \
	printf "$(GREEN)Freed %s MB.$(NC) Available: %s MB\n" "$$freed" "$$after"

.PHONY: ram
ram:
	@free -h | awk '/^Mem:/ {printf "$(BOLD)Memory:$(NC) %s available of %s\n\n", $$7, $$2}'
	@printf "$(BOLD)%8s  %-7s %s$(NC)\n" "RSS" "PID" "PROCESS"
	@ps -eo rss,pid,comm --sort=-rss | grep -iE "studio|java|kotlin|gradle" \
		| grep -v grep \
		| awk '$$1 > 204800 {printf "%7.1fG  %-7s %s\n", $$1/1048576, $$2, $$3}' \
		| head -8
	@pgrep -f "studio.sh|android-studio" >/dev/null 2>&1 \
		&& printf "\n$(YELLOW)Android Studio is running — closing it frees the most.$(NC)\n" || true

.PHONY: clean
clean:
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
