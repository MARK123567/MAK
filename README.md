# mak

The code in master branch is under development. The latest release for each network can be found in the [Releases section](https://github.com/MARK123567/MAK/releases). You can switch to the corresponding tag and build the application.

## Documentation

We maintain an extensive [Wiki](https://github.com/MARK123567/MAK/wiki) that covers most topics.  If there's something you need to know that isn't covered there , please ask us either by filing an issue [here](https://github.com/MARK123567/MAK/issues).  We're happy to help with anything!

## Compiling Packages from source

It is only possible to create deb and fat jar packages.

### Install SBT (Scala Build Tool)

For Ubuntu/Debian:

```
echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
sudo apt-get update
sudo apt-get install sbt
```

You can install sbt on Mac OS X using Homebrew.

### Create Package

Clone this repo and execute

```
sbt packageAll
```

.deb and .jar packages will be in /package folder. To build testnet .deb packages use

```
sbt -Dnetwork=testnet packageAll
```

# Running Tests

Execute

`sbt test`

**Note**

If you prefer to work with _SBT_ in the interactive mode, open it with settings:
```bash
SBT_OPTS="${SBT_OPTS} -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled" sbt
```

to solve the `Metaspace error` problem.

