import xml.etree.ElementTree as ET
import numpy as np

def analisar_cenario(nome_ficheiro):
    try:
        tree = ET.parse(nome_ficheiro)
        root = tree.getroot()
        
        duracoes = []
        perdas_tempo = []
        
        # Ignorar as máquinas da obra, focar apenas no tráfego
        for trip in root.findall('tripinfo'):
            if "obra" not in trip.get('id'): 
                duracoes.append(float(trip.get('duration')))
                perdas_tempo.append(float(trip.get('timeLoss')))
        
        if len(duracoes) > 0:
            media_duracao = np.mean(duracoes)
            p95_duracao = np.percentile(duracoes, 95)
            media_perda = np.mean(perdas_tempo)
            p95_perda = np.percentile(perdas_tempo, 95)
            
            print(f"--- Resultados para: {nome_ficheiro} ---")
            print(f"Veículos analisados: {len(duracoes)}")
            print(f"Tempo Viagem - Média: {media_duracao:.2f}s | p95: {p95_duracao:.2f}s")
            print(f"TimeLoss     - Média: {media_perda:.2f}s | p95: {p95_perda:.2f}s\n")
            
            return media_duracao, p95_duracao, media_perda, p95_perda
        else:
            print(f"Nenhum veículo encontrado em {nome_ficheiro}\n")
            
    except FileNotFoundError:
        print(f"Ficheiro {nome_ficheiro} não encontrado.\n")

# Executar a análise
print("A extrair dados e calcular Percentis (p95)...\n")
analisar_cenario('tripinfo_0.xml')
analisar_cenario('tripinfo_25.xml')
analisar_cenario('tripinfo_50.xml')
analisar_cenario('tripinfo_75.xml')
analisar_cenario('tripinfo_100.xml')