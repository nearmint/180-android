package fr.thermostat6.app180.data.model

import com.google.gson.annotations.SerializedName

data class Category(
    @SerializedName("id")     val id: Int,
    @SerializedName("count")  val count: Int,
    @SerializedName("name")   val name: String,
    @SerializedName("slug")   val slug: String,
    @SerializedName("parent") val parent: Int
)
