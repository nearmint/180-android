package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

data class Media(
    @SerializedName("id")         val id: Int,
    @SerializedName("source_url") val sourceURL: String
)
