# kahuin

## Design

### External library docs

## Development

### Prerequisites

* JVM, to compile ClojureScript
* NPM, to manage Javascript dependencies and run scripts

### Run in watch mode

Make sure dependencies are installed (`npm install`), then run:
```
npm run watch
```

This will execute `shadow-cljs` to make the app available at
 http://localhost:8000. Code will autoreload on change.

Tests will also rerun, and the output is available at http://localhost:8020

To run a browser REPL, connect to nREPL at 7000, then run:

```clojure
(shadow/repl :browser)  ; or :test
```

#### Build a production version

Make sure dependencies are installed (`npm install`), then run
```
npm run release
```

The files will be output in the `release` directory.

## Deployment

## License