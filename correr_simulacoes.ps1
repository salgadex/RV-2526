# Script de Automação MOSAIC - Extração Segura e Recursiva de Tripinfos
$penetrations = @("0", "25", "50", "75", "100")
$mosaicPath = "C:\Users\Luis\Desktop\RV_\eclipse-mosaic-25.2"
$scenarioName = "Roadworks"
$resultsFolder = "$mosaicPath\scenarios\$scenarioName\resultados_tese"

# Criar pasta de resultados limpa
if (!(Test-Path -Path $resultsFolder)) {
    New-Item -ItemType Directory -Force -Path $resultsFolder
}

foreach ($pen in $penetrations) {
    Write-Host "===================================================" -ForegroundColor Cyan
    Write-Host " A INICIAR SIMULAÇÃO PARA $pen% DE PENETRAÇÃO V2X" -ForegroundColor Cyan
    Write-Host "===================================================" -ForegroundColor Cyan

    # 1. Preparar o Mapping correto
    $sourceMapping = "$mosaicPath\scenarios\$scenarioName\mapping\mapping_$pen.json"
    $targetMapping = "$mosaicPath\scenarios\$scenarioName\mapping\mapping_config.json"
    Copy-Item -Path $sourceMapping -Destination $targetMapping -Force
    Write-Host "[OK] Ficheiro de mapping configurado para $pen%."

    # 2. Correr o MOSAIC (modo headless nativo)
    Set-Location $mosaicPath
    .\mosaic.bat -s $scenarioName

    # 3. Encontrar a pasta de log MAIS RECENTE
    $latestLogDir = Get-ChildItem -Path "$mosaicPath\logs" -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    
    # 4. Pesquisa Recursiva do tripinfo.xml (Procura em todas as subpastas)
    $tripInfoFile = Get-ChildItem -Path $latestLogDir.FullName -Filter "tripinfo.xml" -Recurse | Select-Object -First 1

    if ($null -ne $tripInfoFile) {
        $destino = "$resultsFolder\tripinfo_$pen.xml"
        Copy-Item -Path $tripInfoFile.FullName -Destination $destino -Force
        Write-Host "[SUCESSO] Ficheiro tripinfo_$pen.xml extraído em segurança!" -ForegroundColor Green
    } else {
        Write-Host "[ERRO CRÍTICO] Ficheiro tripinfo.xml não foi gerado pelo SUMO na pasta $($latestLogDir.Name)!" -ForegroundColor Red
    }
}

Write-Host "Todas as simulações concluídas. Verifica a pasta 'resultados_tese'." -ForegroundColor Yellow