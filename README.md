# skLambda

A Skript addon adding **first-class lambdas** and a **declarative `listen` section** with countdowns, trigger limits, and lifecycle callbacks.

- **Lambdas** — define inline anonymous functions, pass them as values, call/run them later
- **`listen` section** — register temporary event listeners with a `countdown:`, a `triggers:` budget, and `on trigger` / `on completion` / `on timeout` callbacks
- **`where:` filters** — pre-filter events with one or more conditions before they reach `on trigger`
- **Runtime control** — `pause`, `resume`, `cancel listener`, `skip trigger`, mutate `triggers` and `countdown` from anywhere

## Showcase: with vs without skLambda

A 30-second / 10-block stone-mining challenge that rewards a diamond on completion or scolds the player on timeout.

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
        delete {challenge::task::%player%}

command /challenge:
    trigger:
        set {challenge::%player%} to true
        set {challenge::progress::%player%} to 0
        send "mine 10 stone in 30s" to player
        set {challenge::task::%player%} to a new task that runs in 30 seconds:
            if {challenge::%player%} is set:
                send "too slow" to player
                delete {challenge::%player%}
                delete {challenge::progress::%player%}
                delete {challenge::task::%player%}
```

Notes on what we had to do by hand: a global `on break of stone` that fires for *every* player, an external `{challenge::%player%}` flag to gate it, a progress counter, manual timeout via a separate scheduled task, and three `delete` lines per exit path to clean up.

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

The listener is per-invocation, the counter and timeout are built in, and teardown happens automatically when either callback fires.

## Quick reference

### Lambda

```applescript
set {_double} to lambda (n: number) -> number:
    return {_n} * 2

set {_x} to call lambda {_double} with 5     # 10
run lambda {_greet} with player              # fire-and-forget
```

### Listen

```applescript
listen for damage:
    where:
        victim is {_p}
        damage cause is fall
    countdown: 30 seconds
    triggers: 5
    on trigger:
        if attacker is not a player:
            skip trigger                     # ignore this event, don't consume a trigger
        cancel event
    on completion:
        send "shield used up" to {_p}
    on timeout:
        send "shield expired" to {_p}
```

### Runtime control

```applescript
set {shield} to listener for damage where victim is {_p}:
    ...
register {shield}

pause {shield}
resume {shield}

add 10 seconds to {shield}'s countdown
set triggers of {shield} to 3

if {shield} is registered:
    ...
if {shield} is paused:
    resume {shield}

unregister {shield}        # silent stop from outside
# or, inside `on trigger`:
cancel listener            # silent stop from inside
```

`remaining triggers` and `remaining countdown` are usable inside any of the three callbacks.

## Build

```bash
./gradlew build
# → build/libs/skLambda-<version>.jar
```

## License

[MIT](LICENSE)
