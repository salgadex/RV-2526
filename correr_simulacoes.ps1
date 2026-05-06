# Script de Automação MOSAIC - Extração de TripInfo e Queue Metrics
$penetrations = @( "100")
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
    $tempQueue = "$scenarioFolder\queue_metrics_temp.xml"
    if (Test-Path $tempTripInfo) { Remove-Item -Path $tempTripInfo -Force }
    if (Test-Path $tempQueue) { Remove-Item -Path $tempQueue -Force }

    Set-Location $mosaicPath
    
    # Execução do MOSAIC com Watchdog desligado
    .\mosaic.bat -w 0 -s $scenarioName

    # Extrai o TripInfo
    if (Test-Path $tempTripInfo) {
        $destinoTrip = "$resultsFolder\tripinfo_$pen.xml"
        Move-Item -Path $tempTripInfo -Destination $destinoTrip -Force
        Write-Host "[SUCESSO] Ficheiro tripinfo_$pen.xml extraído com sucesso!" -ForegroundColor Green
    } else {
        Write-Host "[ERRO CRÍTICO] TripInfo não gerado em $tempTripInfo" -ForegroundColor Red
    }

    # Extrai o Queue Metrics (E2)
    if (Test-Path $tempQueue) {
        $destinoQueue = "$resultsFolder\queue_metrics_$pen.xml"
        Move-Item -Path $tempQueue -Destination $destinoQueue -Force
        Write-Host "[SUCESSO] Ficheiro queue_metrics_$pen.xml extraído com sucesso!" -ForegroundColor Green
    } else {
        Write-Host "[ERRO CRÍTICO] Detetores E2 não geraram o ficheiro $tempQueue" -ForegroundColor Red
    }
}

Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "Simulações concluídas. Executa o teu script Python." -ForegroundColor Yellow