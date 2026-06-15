![Cover](https://cdn.modrinth.com/data/cached_images/32070cb0beb1772a327160cb7f52827138ae8c3b.png)

A Skript addon that lets you treat behavior as a value, and cut a lot of boilerplate while you're at it. It adds:

1. **Lambdas:** Small functions you save in a variable, pass around, and run later. They support closures, default parameters, and piping.
2. **Predicates:** Reusable yes/no tests you can combine with all, any, or none, or count how many pass.
3. **List operations:** Map, reduce, scan, zip, sort, highest/lowest, and paging, without writing the loops yourself.
4. **Self-cleaning listeners:** Temporary event listeners scoped to one player or command, with a timer, a hit count, and lifecycle callbacks. They stop themselves.
5. **Async:** Run a lambda off the main thread with futures, `wait for` an event or result without freezing the server, and `watch` a value or condition.

## Links
[![github](https://cdn.modrinth.com/data/cached_images/75ce063aed1ebd362650fad14579ca22f375a392.png)](https://github.com/ahmadmsaleem/skLambda) [![skLambda wiki](https://cdn.modrinth.com/data/cached_images/35010223dc83c95dd3b7a92740ca87eea707d709_0.webp)](https://github.com/ahmadmsaleem/skLambda/wiki)

[![Get on skUnity](https://docs.skunity.com/skunity/library/Docs/Assets/assets/images/buttons/v2/get-the-syntax-square.png)](https://docs.skunity.com/syntax/search/addon:skLambda)[![SkriptHubViewTheDocs](http://skripthub.net/static/addon/ViewTheDocsButton.png)](http://skripthub.net/docs/?addon=skLambda) [![skDocks](https://skdocs.org/viewdocs.png)](https://skdocs.org/docs?addon=skLambda)

## Example: same task, two ways

The task: tell the player to mine 10 stone in 30 seconds. Give a diamond if they finish, say "too slow" if they don't.

### Without skLambda

```applescript
on break of stone:
    if {challenge::%player%} is not set:
        stop
    add 1 to {challenge::progress::%player%}
    if {challenge::progress::%player%} >= 10:
        send "you did it!" to player
        give 1 diamond to player
        delete {challenge::%player%}
        delete {challenge::progress::%player%}

command /challenge:
    trigger:
        set {challenge::%player%} to true
        set {challenge::progress::%player%} to 0
        send "mine 10 stone in 30s" to player
        wait 30 seconds
        if {challenge::%player%} is set:
            send "too slow" to player
            delete {challenge::%player%}
            delete {challenge::progress::%player%}
```

Lots to do by hand: a global `on break of stone` that runs for **every** player, a flag variable, a counter variable, a separate `wait` for the timeout, and `delete` lines everywhere to clean up.

### With skLambda

```applescript
command /challenge:
    trigger:
        send "mine 10 stone in 30s" to player
        listen for block break where event-block is stone:
            countdown: 30 seconds
            triggers: 10
            every 1 second:
                send action bar "%remaining triggers% left — %remaining countdown%" to event-player
            on completion:
                send "you did it!" to event-player
                give 1 diamond to event-player
            on timeout:
                send "too slow!" to event-player
```

That's it. The listener belongs to this one command run. The counter, the timer, and the live action-bar readout are built in, and when it finishes or times out, it cleans up by itself.

## More than listeners

Save a test and run it over a list. Predicates pair with Skript's own filter, and list operations skip the loop:

```applescript
# keep only the players who are AFK
set {_afk} to lambda (p: player): {_p}'s idle time > 5 minutes
set {_idle::*} to all players where [{_afk} passes for input]

# score every player with a lambda, grab the winner
set {_score} to lambda (p: player) -> number:
    return {_p}'s level
set {_mvp} to highest of all players by {_score}
```

New in 1.3.0, you can pause a trigger and wait without freezing the server:

```applescript
command /confirm:
    trigger:
        send "type 'yes' in chat within 15s to confirm." to player
        wait for next chat where player is sender within 15 seconds:
            if message is "yes":
                send "confirmed!" to player
            on timeout:
                send "cancelled — no reply." to player
```

The trigger picks up right where it left off when the player chats, and the server keeps ticking the whole time. You can also `wait for` a background job (`future of calling lambda ...`) or `watch` a value or condition and react only when it changes.

Full guides and dozens of runnable examples are on the [wiki](https://github.com/ahmadmsaleem/skLambda/wiki).

## Requirements
- [Paper](https://papermc.io/) 1.21.1+
- [Skript](https://modrinth.com/plugin/skript) 2.15+

## Using lambdas from Java

If you're writing a Java plugin that talks to skLambda, you can turn a Skript lambda straight into a normal Java functional interface and call it like any other Java function:

- `asPredicate()`
- `asFunction()`
- `asBiFunction()`
- `asConsumer()`
- `asSupplier()`

Pick the one that matches your lambda's shape: its argument count and whether it returns a value. Added in 1.1.0.

## Build

```bash
./gradlew build # build/libs/skLambda-<version>.jar
```

## License

[MIT](https://github.com/ahmadmsaleem/skLambda/blob/main/LICENSE)

## Stats

skLambda uses [bStats](https://bstats.org/plugin/bukkit/skLambda/31630) for anonymous usage stats. You can opt out in `plugins/bStats/config.yml`.

[![bStats](https://bstats.org/signatures/bukkit/skLambda.svg)](https://bstats.org/plugin/bukkit/skLambda/31630)


## NOTES

if you experience any problems, let me know on discord [eult](https://discord.com/users/670375448508629012), [discord server](https://discord.gg/peTA4AugHb)
