<!--
Copyright (c) Sponge Data Inc. All rights reserved.

-->

# Spongebase Driver for YCSB
This driver is a binding for the YCSB facilities to operate against a Spongebase Server cluster. It uses the official Spongebase Java SDK and provides a rich set of configuration options.

## Quickstart

### 1. Start Spongebase Server
You need to start a single node or a cluster to point the client at. 

### 2. Set up YCSB
You need to clone the repository and compile everything.

```
git clone git@bitbucket.org:bcui6611/ycsb.git
cd ycsb
mvn clean package
```

### 3. Run the Workload
Before you can actually run the workload, you need to "load" the data first.

```
bin/ycsb load spongebase -s -P workloads/workloada
```

Then, you can run the workload:

```
bin/ycsb run spongebase -s -P workloads/workloada
```

Please see the general instructions in the `doc` folder if you are not sure how it all works. You can apply a property (as seen in the next section) like this:

```
bin/ycsb run spongebase -s -P workloads/workloada -p spongebase.useJson=false
```

## Scans not supported in the SpongebaseClient

## Configuration Options
Since no setup is the same and the goal of YCSB is to deliver realistic benchmarks, here are some setups that you can tune. Note that if you need more flexibility (let's say a custom transcoder), you still need to extend this driver and implement the facilities on your own.

You can set the following properties (with the default settings applied):

 - spongebase.url=http://127.0.0.1:8091/pools => The connection URL from one server.
 - spongebase.bucket=default => The bucket name to use.
 - spongebase.password= => The password of the bucket.
 - spongebase.checkFutures=true => If the futures should be inspected (makes ops sync).
 - spongebase.persistTo=0 => Observe Persistence ("PersistTo" constraint).
 - spongebase.replicateTo=0 => Observe Replication ("ReplicateTo" constraint).
 - spongebase.json=true => Use json or java serialization as target format.

