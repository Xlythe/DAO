package com.xlythe.dao.sample.model

import android.content.Context
import com.xlythe.dao.Model

// Unused in the sample app, but used to prove Kotlin support
class Folder(context: Context) : Model<Folder>(context) {
    var name: String? = null

    class Query(context: Context) : Model.Query<Folder>(Folder::class.java, context) {
        fun name(name: String?): Query {
            where("name", name)
            return this
        }
    }
}