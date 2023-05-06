
# jlink
```shell

# x86
/Users/guoxiaomin/work/jdk/zulu-17.jdk/Contents/Home/bin/jlink --module-path /Users/guoxiaomin/work/jdk/zulu-17.jdk/Contents/Home/jmods --add-modules java.base,java.xml,java.se,jdk.unsupported,java.desktop,jdk.crypto.cryptoki,jdk.crypto.ec --output jre


# arm
/Users/guoxiaomin/work/jdk/zulu17.jdk.arm/bin/jlink --module-path /Users/guoxiaomin/work/jdk/zulu17.jdk.arm/zulu-17.jdk/Contents/Home/jmods  --add-modules java.base,java.xml,java.se,jdk.unsupported,java.desktop,jdk.crypto.cryptoki,jdk.crypto.ec --output jre17
```

# macos jpackage
```shell
cd /Users/guoxiaomin/work/cosy

# --main-class com.example.myapp.Main \
# --icon myapp.icns \

# x86
/Users/guoxiaomin/work/jdk/zulu-17.jdk/Contents/Home/bin/jpackage \
--type app-image \
--input cosy-tunnel-app \
--dest ./cosy-tunnel-out \
--name "cosy-tunnel" \
--main-jar cosy-tunnel.jar \
--app-version 1.0.0 \
--mac-package-identifier cn.com.apexedu.cosytunnel \
--mac-package-name "cosy-tunnel" \
--vendor "cosy" \
--java-options "-Xmx128m" \
--runtime-image /Users/guoxiaomin/work/jdk/jre

# arm
/Users/guoxiaomin/work/jdk/zulu17.jdk.arm/bin/jpackage \
--type app-image \
--input cosy-tunnel-app \
--dest ./cosy-tunnel-out \
--name "cosy-tunnel" \
--main-jar cosy-tunnel.jar \
--app-version 1.0.0 \
--mac-package-identifier cn.com.apexedu.cosytunnel \
--mac-package-name "cosy-tunnel" \
--vendor "cosy" \
--java-options "-Xmx128m" \
--runtime-image /Users/guoxiaomin/work/jdk/jre17

```



# windows jpackage
```shell
cd D:\jdk

D:\jdk\zulu17.42.19-ca-jdk17.0.7-win_x64\bin\jlink --module-path D:\jdk\zulu17.42.19-ca-jdk17.0.7-win_x64\jmods  --add-modules java.base,java.xml,java.se,jdk.unsupported,java.desktop,jdk.crypto.cryptoki,jdk.crypto.ec --output jre17


# cmd exec:

D:\jdk\zulu17.42.19-ca-jdk17.0.7-win_x64\bin\jpackage ^
--type app-image ^
--input cosy-tunnel ^
--dest .\cosy-tunnel-out ^
--name "cosy-tunnel" ^
--main-jar cosy-tunnel.jar ^
--app-version 1.0.0 ^
--vendor "cosy" ^
--java-options "-Xmx128m" ^
--runtime-image D:\jdk\jre17

```







