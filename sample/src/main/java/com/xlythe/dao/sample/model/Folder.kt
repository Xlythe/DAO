package com.xlythe.dao.sample.model

import android.content.Context
import com.xlythe.dao.Model
import com.xlythe.dao.Schema
import com.xlythe.dao.Unique

// Unused in the sample app, but used to prove Kotlin support
class Folder(context: Context) : Model<Folder>(context) {
    @Unique
    var id: Int = 0
    @Schema(columnName = "custom_column_name")
    var name: String? = null

    class Query(context: Context) : Model.Query<Folder>(Folder::class.java, context) {
        fun name(name: String?): Query {
            where("name", name)
            return this
        }
    }
}