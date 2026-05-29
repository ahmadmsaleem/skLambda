![Cover](https://cdn.modrinth.com/data/cached_images/32070cb0beb1772a327160cb7f52827138ae8c3b.png)


A Skript addon that adds two things:

1. **Lambdas** — small functions you can save in a variable and run later.
2. **The `listen` section** — a short way to make a temporary event listener with a timer, a hit count, and what to do at the end.
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

## Quick reference

### Lambdas

A lambda is a small function. You can save it, pass it around, and call it later.

```applescript
set {_double} to lambda (n: number) -> number:
    return {_n} * 2

set {_x} to call lambda {_double} with 5     # 10
run lambda {_greet} with player              # just run it, no return value
```

You can also write a lambda **inline** on one line. If the body is a condition,
the lambda becomes a predicate (it returns whether the condition holds);
parameters are available as locals (`{_p}`) inside the body.

```applescript
set {is-op} to lambda (p: player): {_p} is op
add lambda (n: number): {_n} > 0 to {positive-checks::*}
```

### Predicates

`passes` invokes a lambda as a test and checks whether it returned true. With a
list of lambdas it passes only if **all** of them pass.

```applescript
set {is-op} to lambda (p: player): {_p} is op

if {is-op} passes for player:               # one predicate
    send "you're staff" to player

add lambda (p: player): {_p} is op to {is-admin::*}
add lambda (p: player): name of {_p} is "eult" to {is-admin::*}

if {is-admin::*} passes for player:         # true only if ALL pass
    send "you're admin" to player

if {is-admin::*} doesn't pass for player:   # true if at least one fails
    send "not quite admin" to player
```

A predicate runs in its own context, so give it the value(s) it tests as
parameters and supply them after `for`. This also tidies up `listen ... where`:

```applescript
set {is-stone} to lambda (b: block): {_b} is stone
listen for block break where {is-stone} passes for event-block:
    on trigger: send "stone!" to event-player
```

### Listen

A `listen` section makes a temporary event listener. You can give it:

- `where:` — extra rules. The event must match all of them.
- `countdown:` — a time limit.
- `triggers:` — a max number of times it can fire.
- `on trigger:` — what to do each time it fires.
- `on completion:` — what to do when `triggers:` is reached.
- `on timeout:` — what to do when `countdown:` runs out.

```applescript
listen for damage:
    where:
        victim is {_p}
        damage cause is fall
    countdown: 30 seconds
    triggers: 5
    on trigger:
        if attacker is not a player:
            skip trigger          # don't count this hit, keep listening
        cancel event
    on completion:
        send "shield used up" to {_p}
    on timeout:
        send "shield expired" to {_p}
```

Inside any of the three callbacks you can use:

- `remaining triggers` — how many fires are left.
- `remaining countdown` — how much time is left.

### Saving a listener for later

You can also save a listener in a variable and start it whenever you want.

```applescript
set {shield} to listener for damage where victim is {_p}:
    ...
register {shield}
```

### Controlling a listener

```applescript
pause {shield}                          # stop reacting to events, freeze the timer
resume {shield}                         # start again

add 10 seconds to {shield}'s countdown  # change the timer
set triggers of {shield} to 3           # change the hit count

if {shield} is registered:
    ...
if {shield} is paused:
    resume {shield}

unregister {shield}                     # stop it from the outside
```

Inside `on trigger:` you also have:

- `cancel listener` — stop the listener now, don't fire completion or timeout.
- `skip trigger` — ignore this one event, keep listening, don't count it.

## Build

```bash
./gradlew build # build/libs/skLambda-<version>.jar
```

## License

[MIT](LICENSE)
