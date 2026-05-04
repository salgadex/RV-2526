# Gestão Cooperativa de Tráfego em Zona de Obras com V2X
Este repositório contém a implementação do projeto prático de Redes Veiculares. O sistema utiliza o **Eclipse MOSAIC 25.2** e o **SUMO** para simular uma infraestrutura inteligente (RSU) que emite recomendações dinâmicas de velocidade (com histerese) para mitigar o congestionamento numa zona de obras.

## 🛠️ Pré-requisitos
* Eclipse MOSAIC 25.2
* Eclipse SUMO (adicionado às variáveis de ambiente)
* Java 17 e Maven
* Python 3 (com bibliotecas `numpy` e `matplotlib`)

## 🚀 Como Executar e Testar os Diferentes Cenários

### Passo 1: Navegar para a pasta do MOSAIC
Abre o terminal do VS Code (ou PowerShell) e ajusta o caminho para a tua máquina:
```powershell
cd "C:\...\RV_\eclipse-mosaic-25.2\"

Passo 2: Escolher o Cenário (0%, 70% ou 100% V2X)
Na pasta scenarios\Roadworks\mapping\

Para testar um cenário específico:
    - Para 0% editas no mapping_config.json os parametros de todos os veiculos para (ex: para o carro) 
    
    #"carro": [
    # { "name": "carro_v2x", "weight": 0 },
    # { "name": "carro_legacy", "weight": 100 } e fazes igual para os outros

     - Para 70% editas no mapping_config.json os parametros de todos os veiculos para (ex: para o carro) 

      #"carro": [
    # { "name": "carro_v2x", "weight": 70 },
    # { "name": "carro_legacy", "weight": 30 } e fazes igual para os outros

    - Para 100% editas no mapping_config.json os parametros de todos os veiculos para (ex: para o carro) 

      #"carro": [
    # { "name": "carro_v2x", "weight": 100 },
    # { "name": "carro_legacy", "weight": 0 } e fazes igual para os outros
    
    
Abre o ficheiro scenarios\Roadworks\sumo\roadworks.sumocfg e garante que a tag de output reflete o cenário (ex: <tripinfo-output value="tripinfo_100.xml"/>) se estiveres a testar para 100% no mapping_config.json

Passo 3: Executar a Simulação
Corre o seguinte comando no terminal. O parâmetro -w 0 desativa a interface gráfica do SUMO para que a simulação corra de forma ultrarrápida (em poucos segundos):

PowerShell
.\mosaic.bat -s Roadworks -w 0
(Nota: Se quiseres ver os carros e o trânsito visualmente, retira o -w 0).

Passo 4: Extração e Análise de Dados
Após correres os três cenários e teres os ficheiros tripinfo_0.xml, tripinfo_70.xml e tripinfo_100.xml gerados na pasta do SUMO, executa os scripts de análise:

PowerShell
cd scenarios\Roadworks\
python analise.py
python gerar_graficos.py
Isto vai calcular as médias e os percentis 95 (p95) e gerar a imagem grafico_resultados.png com o impacto da nossa solução.