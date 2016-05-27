D(ata) A(access) O(object)
===========
A library for quickly creating objects that have a SQLite database that backs them. These objects are usually called DAOs, or data access objects; lightweight objects that hold information, without a lot of logic inside them outside of getters and setters.

To create a new DAO, extend the Model class, like so.

```java
/**
 * An instance of a game
 */
public class Game extends Model<Game> {
    public Game(Context context) {
        super(Game.class, context);
    }
}
```

Inside the model, we list out all the variables we wish to persist. One of these variables must be marked as @Unique; this will be your key. The key keeps this instance of your DAO unique from another instance with similar data. The other variables must be of type String, int, boolean, long, float, or byte[] -- other types are not supported. If you have a variable that you don't want persisted, mark it as a transient variable (eg. private transient Object mTempData).

```java
// A unique identifier for this object
@Unique
private int id;
// A readable name
private String name;
// Whether or not the game is ongoing
private boolean active;
// The minimum number of players to start the game
private int min_players;
// The maximum number of players to start the game
private int max_players;
```

The Model class comes with a Query inner class, but we can extend it to make it easier to use. Lets add a few convenience methods like id() and name().

```java
public static class Query extends Model.Query<Game> {
    public Query(Context context) {
        super(Game.class, context);
    }

    public Game.Query id(int id) {
        where("id", id);
        return this;
    }

    public Game.Query name(String name) {
        where("name", name);
        return this;
    }

    public Game.Query active(boolean active) {
        where("active", active);
        return this;
    }

    public Game.Query minPlayers(int minPlayers) {
        where("min_players", minPlayers);
        return this;
    }

    public Game.Query maxPlayers(int maxPlayers) {
        where("max_players", maxPlayers);
        return this;
    }
}
```

And, with that done, we can now query our database using the following methods

```java
Game game = new Game.Query(getContext()).id(id).first();
```
```java
List<Game> activeGames = new Game.Query(getContext()).active(true).all();
```
```java
int numOfGames = new Game.Query(getContext()).count();
```
```java
Game game = new Game.Query(getContext()).id(0).name("Hello World").insert();
```

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

With that out of the way, you can now call mGame.save() and mGame.delete() in order to update the state.

The full class of Game is below.

```java
/**
 * An instance of a game
 */
public class Game extends Model<Game> {
    public static void registerObserver(Observer observer) {
        registerObserver(Game.class, observer);
    }

    public static void unregisterObserver(Observer observer) {
        unregisterObserver(Game.class, observer);
    }

    public static class Query extends Model.Query<Game> {
        public Query(Context context) {
            super(Game.class, context);
        }

        public Game.Query id(int id) {
            where(new Param("id", id));
            return this;
        }

        public Game.Query name(String name) {
            where(new Param("name", name));
            return this;
        }

        public Game.Query active(boolean active) {
            where(new Param("active", active));
            return this;
        }

        public Game.Query minPlayers(int minPlayers) {
            where(new Param("min_players", minPlayers));
            return this;
        }

        public Game.Query maxPlayers(int maxPlayers) {
            where(new Param("max_players", maxPlayers));
            return this;
        }
    }

    // A unique identifier for this object
    @Unique
    private int id;
    // A readable name
    private String name;
    // Whether or not the game is ongoing
    private boolean active;
    // The minimum number of players to start the game
    private int min_players;
    // The maximum number of players to start the game
    private int max_players;

    public Game(Context context) {
        super(Game.class, context);
    }

    @Override
    public void save() {
        super.save();
    }

    @Override
    public void delete() {
        super.delete();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public int getMinPlayers() {
        return min_players;
    }

    public int getMaxPlayers() {
        return max_players;
    }

    public List<Player> getPlayers() {
        return new Player.Query(getContext()).gameId(id).all();
    }

    @Override
    public String toString() {
        return "[" + id + "]" + name;
    }
}
```
