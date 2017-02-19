# Sparkling-Notebook
Another notebook application for spark

## Why creating another notebook ?
 * I've been using Jupyter's Spark Kernel, however, it creates JVM and SparkContext for every single notebook, I wasn't happy with it.
 * I tried another Notebook solution which has so many features, I didn't like it. I like Jupyter's user experience more than such complicated app.
 * I wanted to have a simple, stable and portable notebook app.

## What is this actually?
 * It's a scala version of Jupyter server that comes with Jupyter's javascript. So that it provides the exact same user experience as Jupyter does, but it handles Spark natively.
 * This keeps notebooks' format exactly same as Jupyter's one, so you can always go back to Jupyter.
 * No modification to the Jupyter's javascript except for CSS tweak, if you know how to use Jupyter, you can deal with this quickly.

## What it isn't.
* This doesn't aim to support multi user and shared service kind of usage, this is more like a web version of spark-shell.
* No intention to support other launguages, it does Scala and Python only.
* I do think if it would be nice if there's a visualization library for scala, but you should try matplotlib (python) first.

## How it works ?
* If you don't know how to use Jupyter, then please go Jupyter's documentation page.
* The server holds a single SparkContext and configuration can be done in spark-default.conf

## Does this work ?
* No, as of 2/18