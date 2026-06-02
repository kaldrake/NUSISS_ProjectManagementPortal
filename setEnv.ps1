$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:PATH="$env:JAVA_HOME\bin;C:\Program Files\nodejs\;" + $env:PATH

Write-Host "JAVA_HOME = $env:JAVA_HOME"
Write-Host ""
Write-Host "Java version:"
java -version
Write-Host ""
Write-Host "Node version:"
node -v
Write-Host "NPM version:"
npm -v
