D(ata) A(ccess) O(bject)
========================
A library for quickly creating objects that have a SQLite database that backs them. These objects are usually called DAOs, or data access objects; lightweight objects that hold information, without a lot of logic inside them outside of getters and setters.

Where to Download
-----------------
```groovy
dependencies {
  implementation 'com.xlythe:data-access-object:2.2'
}
```

Creating a Model
----------------
To create a new DAO, extend the Model class, like so. Your model must have a constructor that takes a Context. This is the constructor that will be used when inflating new instances.

```java
@Database(version=1, retainDataOnUpgrade=false)
public class Note extends Model<Note> {
    public Note(Context context) {
        super(context);
    }
}
```

Inside the model, list out all the variables you wish to persist. The supported types are String, int, boolean, long, float, or byte[]. If you have a variable that you don't want persisted, mark it as a transient variable. While not required, you can annotate your variables with @Unique. If you do, they will act as a key when saving the DAO.

```java
// The note's title
private String title;
// The note's content
private String body;
// The timestamp of the last update to the note
private long timestamp;

// Not persisted in this example
private transient Object mTempData;
// The key
@Unique
private String id;
```

Querying
--------
The Model class comes with a Query inner class, but we can extend it to make it easier to use. Lets add a few convenience methods like title() and body() so that we can filter against them. We'll also add orderByTimestamp() as a sorting option.

```java
public static class Query extends Model.Query<Note> {
    public Query(Context context) {
        super(Note.class, context);
    }

    public Note.Query title(String title) {
        where(new Param("title", title));
        return this;
    }

    public Note.Query body(String body) {
        where(new Param("body", body));
        return this;
    }

    public Note.Query timestamp(long timestamp) {
        where(new Param("timestamp", timestamp));
        return this;
    }

    public Note.Query orderByTimestamp() {
        orderBy("timestamp");
        return this;
    }

    public Note.Cursor cursor() {
        return new Note.Cursor(getContext(), super.cursor());
    }
}
```

And, with that done, we can now query our database using the following methods

```java
Note note = new Note.Query(getContext()).first();
```
```java
List<Note> notes = new Note.Query(getContext()).title("Hello World").all();
```
```java
int numOfNotes = new Note.Query(getContext()).count();
```
```java
Note note = new Note.Query(getContext()).title("Hello World").body("This is my note.").insert();
```

Saving and Deleting
-------------------
To edit or delete an entry, you'll have to expose the methods. They're marked as protected methods in Model, making all DAOs write-once by default.

```java
@Override
public void save() {
    super.save();
}

@Override
public void delete() {
    super.delete();
}
```

With that out of the way, you can now call mNote.save() and mNote.delete() in order to update the state.

Updating the Version
--------------------
It's not uncommon to realize, belatedly, that you want to add another field to your DAO. At the top of the Note class, the database version and update strategy is listed. By default, DAOs start at version 1 and completely wipe the database when the version is incremented.

```java
@Database(version=1, retainDataOnUpgrade=false)
```

If you set retainDataOnUpgrade to true, then you must mark newly added fields with the @Version(value=VERSION) annotation, where VERSION is when the field was added.
```java
@Version(value=2)
private int priority;
```

Summary
-------
See the [full Note class](sample/src/main/java/com/xlythe/dao/sample/model/Note.java) inside the sample.

Remove Servers
--------------

DAO also has the ability to mirror remote databases if there's an available REST API. Instead of extending Model, extend RemoteModel.

```java
@Database(version=1, retainDataOnUpgrade=false)
public class Note extends RemoteModel<Note> {
    public Note(Context context) {
        super(context);
    }
}
```

Your Query's all(), first(), and insert() methods all now come with optional Callback parameters, as well as a new url() method.

```java
List<Note> cache = new Note.Query(getContext()).url("https://your.website.here/note").title("Hello World").all(new Callback<List<Note>>() {
    @Override
    public void onSuccess(List<Note> note) {
        // TODO use the data from the server
    }

    @Override
    public void onFailure(Throwable throwable) {
        Log.e(TAG, "Failed to reach server", throwable);
    }
});
```

To get more control over the connection, call RemoteModel.setServer().

```java
RemoteModel.setServer(new Server() {
    @Override
    public void get(String url, JSONObject params, Callback<JSONResult> callback) {}

    @Override
    public void post(String url, JSONObject params, Callback<JSONResult> callback) {}

    @Override
    public void put(String url, JSONObject params, Callback<JSONResult> callback) {}

    @Override
    public void delete(String url, Callback<JSONResult> callback) {}
});
```

And lastly, don't forget the internet permission.
```xml
<uses-permission android:name="android.permission.INTERNET" />
```
