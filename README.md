__This software is ALPHA.__

# Ring HAP

[![Build Status](https://travis-ci.org/alexanderkiel/ring-hap.svg?branch=master)](https://travis-ci.org/alexanderkiel/ring-hap)

Ring middleware for the [Hypermedia Application Protocol][1].

## Install

To install, just add the following to your project dependencies:

```clojure
[org.clojars.akiel/ring-hap "0.4"]
```

## Usage

```clojure
(require '[ring-hap.core :refer [wrap-hap]])

(-> handler
    (wrap-hap))
```

## Example Applications

The following applications use Ring HAP already:

 * [HAP ToDo][2]

## License

Copyright © 2015 Alexander Kiel

Distributed under the Eclipse Public License, the same as Clojure.

[1]: <https://github.com/alexanderkiel/hap-spec>
[2]: <https://github.com/alexanderkiel/hap-todo>
