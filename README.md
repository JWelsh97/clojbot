# Clojbot

This project contains a bare-minimum IRC bot written in Clojure. The main idea of the bot is to be as resilient as possible. But above all, it should be clean code and maintainable (i.e., comments are very welcome).

## Usage

`lein run`. Be sure to configure the bot properly in `conf/servers.end`. If you want to use the youtube module you will also have to provide a valid API key in `conf/youtube.edn`.

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
