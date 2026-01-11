
@file:kotlin.Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "LocalVariableName",
  "MayBeConstant",
  "RedundantVisibilityModifier",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "LocalVariableName",
  "unused",
)

package com.google.firebase.dataconnect.generated



public interface CreateUserMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ExampleConnector,
      CreateUserMutation.Data,
      CreateUserMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val displayName: String,
    val email: com.google.firebase.dataconnect.OptionalVariable<String?>,
    val photoUrl: com.google.firebase.dataconnect.OptionalVariable<String?>
  ) {
    
    
      
      @kotlin.DslMarker public annotation class BuilderDsl

      @BuilderDsl
      public interface Builder {
        public var displayName: String
        public var email: String?
        public var photoUrl: String?
        
      }

      public companion object {
        @Suppress("NAME_SHADOWING")
        public fun build(
          displayName: String,
          block_: Builder.() -> Unit
        ): Variables {
          var displayName= displayName
            var email: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            var photoUrl: com.google.firebase.dataconnect.OptionalVariable<String?> =
                com.google.firebase.dataconnect.OptionalVariable.Undefined
            

          return object : Builder {
            override var displayName: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { displayName = value_ }
              
            override var email: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { email = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            override var photoUrl: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { photoUrl = com.google.firebase.dataconnect.OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              displayName=displayName,email=email,photoUrl=photoUrl,
            )
          }
        }
      }
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val user_insert: UserKey
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "CreateUser"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun CreateUserMutation.ref(
  
    displayName: String,
  
    block_: CreateUserMutation.Variables.Builder.() -> Unit = {}
  
): com.google.firebase.dataconnect.MutationRef<
    CreateUserMutation.Data,
    CreateUserMutation.Variables
  > =
  ref(
    
      CreateUserMutation.Variables.build(
        displayName=displayName,
  
    block_
      )
    
  )

public suspend fun CreateUserMutation.execute(
  
    displayName: String,
  
    block_: CreateUserMutation.Variables.Builder.() -> Unit = {}
  
  ): com.google.firebase.dataconnect.MutationResult<
    CreateUserMutation.Data,
    CreateUserMutation.Variables
  > =
  ref(
    
      displayName=displayName,
  
    block_
    
  ).execute()


