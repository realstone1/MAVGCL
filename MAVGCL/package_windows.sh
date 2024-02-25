#! /bin/bash

jlink --no-header-files --no-man-pages --compress=2 --strip-debug --add-modules java.se,jdk.httpserver,javafx.controls,javafx.fxml --output ./target/custom_jre --module-path ./jmods

jpackage --input target/deploy/ --name MAVGAnalysis --main-jar MAVGCL-0.8.0.jar --main-class com.comino.flight.MainApp --type msi --icon target/deploy/MAVGAnalysis.ico --runtime-image ./target/custom_jre --java-options '-Djava.library.path=./native --add-opens javafx.base/com.sun.javafx.collections=ALL-UNNAMED -XX:+UnlockExperimentalVMOptions  '
