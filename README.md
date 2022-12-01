# Monitor Node

The monitor project collects data and sends it to the Graphite data source which feeds the Grafana frontend.

## Monitor tasks

### Monitoring of seed nodes

We use 3 approaches to collect data about the state of the seed nodes.

1. The seed nodes send per clear net messages to the monitoring server. Those messages contain information about the
   state of messages, used memory and DAO hashes.
2. A tor node pings on a regular interval all seed nodes to measure round trip time and to see if the seed node is
   reachable over tor.
3. The seed nodes send via the collectd framework server load data which will get dumped to Graphite.

### Monitoring of Tor network

We perform several activities on the Tor network to see the health of the network.

1. Measure startup time for a Tor node
2. Measure time how long it takes to publish a hidden service
3. Measure time how long it takes to create a connection to a group of non-Bisq related hidden services

## Data collection tasks

### Dumping price node data and BTC miner-fees

Request price and BTC miner-fee data from price nodes and dump it to Graphite.

### Dumping trade statistics

Run a headless Bisq node and send trade statistic data to Graphite.

### Dumping offers

Run a headless Bisq node and send offer data to Graphite.

### Dumping DAO data

Run a headless Bisq node and send DAO data to Graphite.

## Building source code

This repo has a dependency on git submodule [bisq](https://github.com/bisq-network/bisq). There are two ways to clone it
before it can be compiled:

```
# 1) Use the --recursive option in the clone command:
$ git clone --recursive https://github.com/bisq-network/bisq-monitor.git

# 2) Do a normal clone, and pull down the bisq repo dependency with two git submodule commands:
$ git clone https://github.com/bisq-network/bisq-monitor.git
$ cd bisq-monitor
$ git submodule init
$ git submodule update
```

To build:

```
$ ./gradlew clean build
```

To update submodule:

```
$ git submodule update --remote
```

