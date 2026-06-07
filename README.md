![Cover](https://cdn.modrinth.com/data/cached_images/32070cb0beb1772a327160cb7f52827138ae8c3b.png)

A Skript addon that adds two things:

1. **Lambdas**: small functions you can save in a variable and run later.
2. **The `listen` section**: a short way to make a temporary event listener with a timer, a hit count, and what to do at the end.
## Links
[![github](https://cdn.modrinth.com/data/cached_images/75ce063aed1ebd362650fad14579ca22f375a392.png)](https://github.com/ahmadmsaleem/skLambda)        [![skLambda wiki](https://cdn.modrinth.com/data/cached_images/35010223dc83c95dd3b7a92740ca87eea707d709_0.webp)
 ](https://github.com/ahmadmsaleem/skLambda/wiki)
 
[![SkriptHubViewTheDocs](http://skripthub.net/static/addon/ViewTheDocsButton.png)](http://skripthub.net/docs/?addon=skLambda) [![skDocks](https://skdocs.org/viewdocs.png)](https://skdocs.org/docs?addon=skLambda)


## Example: same task, two ways

The task: tell the player to mine 10 stone in 30 seconds. Give a diamond if they finish. Say "too slow" if they don't.

### Without skLambda

```applescript
on break of stone:
    if {challenge::%player%} is not set:
        stop
    add 1 to {challenge::progress::%player%}
    send "keep going..." to player
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

This works, but you have to do a lot by hand:
- A global `on break of stone` that runs for **every** player on the server.
- A flag variable to know which player is in the challenge.
- A counter variable.
- A separate `wait 30 seconds` to handle the timeout.
- Lots of `delete` lines to clean up.

### With skLambda

```applescript
command /challenge:
    trigger:
        send "mine 10 stone in 30s" to player
        listen for block break where event-block is stone:
            countdown: 30 seconds
            triggers: 10
            on trigger:
                send "keep going... (%remaining triggers% left)" to event-player
            on completion:
                send "you did it!" to event-player
                give 1 diamond to event-player
            on timeout:
                send "too slow!" to event-player
```

That's it. The listener belongs to this one command run. The counter and the timer are built in. When it finishes or times out, it cleans up by itself.


## Build

```bash
./gradlew build # build/libs/skLambda-<version>.jar
```

## License

[MIT](LICENSE)
