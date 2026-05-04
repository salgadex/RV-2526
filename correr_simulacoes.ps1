# Script de Automação MOSAIC - Extração Segura por Caminho Absoluto
$penetrations = @("0", "25", "50", "75", "100")
$mosaicPath = "C:\Users\Luis\Desktop\RV_\eclipse-mosaic-25.2"
$scenarioName = "Roadworks"
$scenarioFolder = "$mosaicPath\scenarios\$scenarioName"
$resultsFolder = "$scenarioFolder\resultados_tese"

if (!(Test-Path -Path $resultsFolder)) {
    New-Item -ItemType Directory -Force -Path $resultsFolder
}

foreach ($pen in $penetrations) {
    Write-Host "===================================================" -ForegroundColor Cyan
    Write-Host " A INICIAR SIMULAÇÃO PARA $pen% DE PENETRAÇÃO V2X" -ForegroundColor Cyan
    Write-Host "===================================================" -ForegroundColor Cyan

    $sourceMapping = "$scenarioFolder\mapping\mapping_$pen.json"
    $targetMapping = "$scenarioFolder\mapping\mapping_config.json"
    Copy-Item -Path $sourceMapping -Destination $targetMapping -Force
    Write-Host "[OK] Ficheiro de mapping configurado para $pen%."

    # Limpa vestígios de execuções anteriores
    $tempTripInfo = "$scenarioFolder\tripinfo_temp.xml"
    if (Test-Path $tempTripInfo) { Remove-Item -Path $tempTripInfo -Force }

    Set-Location $mosaicPath
    .\mosaic.bat -s $scenarioName

    # Extrai o ficheiro do caminho absoluto
    if (Test-Path $tempTripInfo) {
        $destino = "$resultsFolder\tripinfo_$pen.xml"
        Move-Item -Path $tempTripInfo -Destination $destino -Force
        Write-Host "[SUCESSO] Ficheiro tripinfo_$pen.xml extraído com sucesso!" -ForegroundColor Green
    } else {
        Write-Host "[ERRO CRÍTICO] O SUMO não gerou o ficheiro em $tempTripInfo" -ForegroundColor Red
    }
}

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "Simulações concluídas. Executa o teu script Python." -ForegroundColor Yellow