@echo off

set THIS_DIR=%~dp0
set VERSION_NUMBER=::VAR_VERSION::

%THIS_DIR%\hd-jre\bin\java ^
    -Dhaberdasher.logging.path="%THIS_DIR%\hd.log" ^
    -jar "%THIS_DIR%\hd-client-%VERSION_NUMBER%.jar" ^
    %*

