
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


import kotlinx.coroutines.flow.filterNotNull as _flow_filterNotNull
import kotlinx.coroutines.flow.map as _flow_map


public interface ListDocumentsInFolderQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ExampleConnector,
      ListDocumentsInFolderQuery.Data,
      ListDocumentsInFolderQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val folderId: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val folder: Folder?
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class Folder(
  
    val documents_via_DocumentFolder: List<DocumentsViaDocumentFolderItem>
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class DocumentsViaDocumentFolderItem(
  
    val id: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.UUIDSerializer::class) java.util.UUID,
    val title: String,
    val extractedText: String,
    val imageUrl: String,
    val lastModifiedAt: @kotlinx.serialization.Serializable(with = com.google.firebase.dataconnect.serializers.TimestampSerializer::class) com.google.firebase.Timestamp?,
    val tags: List<String>?
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "ListDocumentsInFolder"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun ListDocumentsInFolderQuery.ref(
  
    folderId: java.util.UUID,
  
  
): com.google.firebase.dataconnect.QueryRef<
    ListDocumentsInFolderQuery.Data,
    ListDocumentsInFolderQuery.Variables
  > =
  ref(
    
      ListDocumentsInFolderQuery.Variables(
        folderId=folderId,
  
      )
    
  )

public suspend fun ListDocumentsInFolderQuery.execute(
  
    folderId: java.util.UUID,
  
  
  ): com.google.firebase.dataconnect.QueryResult<
    ListDocumentsInFolderQuery.Data,
    ListDocumentsInFolderQuery.Variables
  > =
  ref(
    
      folderId=folderId,
  
    
  ).execute()


  public fun ListDocumentsInFolderQuery.flow(
    
      folderId: java.util.UUID,
  
    
    ): kotlinx.coroutines.flow.Flow<ListDocumentsInFolderQuery.Data> =
    ref(
        
          folderId=folderId,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

