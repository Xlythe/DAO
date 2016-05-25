D(ata) A(access) O(object)
===========
A library for quickly creating objects that have a SQLite database that backs them.

Creating a new DAO is easy. Extend the Model class, like so.

```
/**
 * An instance of a game
 */
public class Game extends Model<Game> {
    public Game(Context context) {
        super(Game.class, context);
    }
}
```

Inside the model, we'll list out all the variables we wish to persist. You must mark one of these variables as @Unique; this will be your key. The other variables must be of type String, int, boolean, long, float, or byte[]. If you don't want to persist a variable, or if you want to use a type other than the ones mentioned before, you must mark that variable as 
transient.

```
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

In order to make queries easy, lets make a static inner class that extends Model.Query.

```
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


```
/**
 * An instance of a game
 */
public class Game extends Model<Game> {
    public static void registerObserver(Observer observer) {
        registerObserver(Game.class, observer);
    }

    public static void unregisterObserver(Observer observer) {
        unregisterObserver(observer);
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