param([ValidateSet('all','forge','fabric','neoforge')][string]$Platform='all')
$ErrorActionPreference='Stop'
$targets=@{forge='forge-1.20.1';fabric='fabric-1.20.1';neoforge='neoforge-1.21.1'}
$selected=if($Platform -eq 'all'){@('forge','fabric','neoforge')}else{@($Platform)}
$delivery=Join-Path $PSScriptRoot 'dist/1.0.0'
New-Item -ItemType Directory -Path $delivery -Force | Out-Null
foreach($name in $selected){
    $project=Join-Path $PSScriptRoot ('platforms/'+$targets[$name])
    Push-Location -LiteralPath $project
    try{
        & .\gradlew.bat build --console=plain
        if($LASTEXITCODE -ne 0){throw "Build failed: $name"}
        $jar=Join-Path $project ('build/libs/block-remap-'+$targets[$name]+'-1.0.0.jar')
        if(!(Test-Path -LiteralPath $jar)){throw "Missing release JAR: $jar"}
        Copy-Item -LiteralPath $jar -Destination $delivery -Force
    }finally{Pop-Location}
}
Get-ChildItem -LiteralPath $delivery -Filter '*.jar' | Get-FileHash -Algorithm SHA256
