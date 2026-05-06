import xml.etree.ElementTree as ET
import matplotlib.pyplot as plt
import numpy as np
import os

# Configurações iniciais
penetrations = [0, 25, 50, 75, 100]
results_folder = "resultados_tese"

# Dicionários para guardar os resultados
throughput = []
timeloss_p95 = []
traveltime_mean = []
halting_vehicles_max = []
jam_length_max = []

print("A processar métricas...")

for pen in penetrations:
    trip_file = os.path.join(results_folder, f"tripinfo_{pen}.xml")
    queue_file = os.path.join(results_folder, f"queue_metrics_{pen}.xml")
    
    # --- 1. EXTRAÇÃO DO TRIPINFO (Throughput, TimeLoss, TravelTime) ---
    if os.path.exists(trip_file):
        tree = ET.parse(trip_file)
        root = tree.getroot()
        
        times = []
        timelosses = []
        count = 0
        
        for trip in root.findall('tripinfo'):
            times.append(float(trip.get('duration')))
            timelosses.append(float(trip.get('timeLoss')))
            count += 1
            
        throughput.append(count)
        traveltime_mean.append(np.mean(times) if times else 0)
        timeloss_p95.append(np.percentile(timelosses, 95) if timelosses else 0)
    else:
        print(f"[AVISO] Falta o ficheiro {trip_file}")
        throughput.append(0); traveltime_mean.append(0); timeloss_p95.append(0)

    # --- 2. EXTRAÇÃO DOS QUEUE METRICS (Halting Vehicles, Jam Length) ---
    if os.path.exists(queue_file):
        tree = ET.parse(queue_file)
        root = tree.getroot()
        
        halting = []
        jam_len = []
        
        for interval in root.findall('interval'):
            # Nomes corretos dos atributos do Detetor E2 do SUMO
            halting_str = interval.get('maxJamLengthInVehicles', '0')
            jam_str = interval.get('maxJamLengthInMeters', '0')
            
            halting.append(int(float(halting_str)))
            jam_len.append(float(jam_str))
            
        # Para provar o pior caso da fila
        halting_vehicles_max.append(max(halting) if halting else 0)
        jam_length_max.append(max(jam_len) if jam_len else 0)
    else:
        print(f"[AVISO] Falta o ficheiro {queue_file}")
        halting_vehicles_max.append(0); jam_length_max.append(0)

# ==========================================
# GERAÇÃO DOS GRÁFICOS (Estilo Artigo LNCS)
# ==========================================
plt.style.use('ggplot')

# 1. Gráfico de TimeLoss (p95) e Escoamento (Throughput)
fig, ax1 = plt.subplots(figsize=(8, 5))
color = 'tab:red'
# --- ALTERAÇÃO DO EIXO X AQUI ---
ax1.set_xlabel('Veículos Equipados com V2X (%)', fontweight='bold')
ax1.set_ylabel('TimeLoss p95 (segundos)', color=color, fontweight='bold')
ax1.plot(penetrations, timeloss_p95, marker='o', color=color, linewidth=2, label='TimeLoss (p95)')
ax1.tick_params(axis='y', labelcolor=color)

ax2 = ax1.twinx()  
color = 'tab:blue'
ax2.set_ylabel('Escoamento (Veículos Concluídos)', color=color, fontweight='bold')
ax2.plot(penetrations, throughput, marker='s', linestyle='--', color=color, linewidth=2, label='Throughput')
ax2.tick_params(axis='y', labelcolor=color)

plt.title('Impacto do V2X no Atraso Crítico e Escoamento', fontweight='bold')
fig.tight_layout()
plt.savefig('grafico_timeloss_throughput.png', dpi=300)
plt.close()

# 2. Gráfico do Comprimento da Fila vs Veículos Parados (Stop-and-Go)
fig, ax1 = plt.subplots(figsize=(8, 5))
color = 'tab:purple'
# --- ALTERAÇÃO DO EIXO X AQUI ---
ax1.set_xlabel('Veículos Equipados com V2X (%)', fontweight='bold')
ax1.set_ylabel('Extensão Máx. da Fila (metros)', color=color, fontweight='bold')
ax1.bar(penetrations, jam_length_max, width=8, color=color, alpha=0.6, label='Extensão Fila')
ax1.tick_params(axis='y', labelcolor=color)

ax2 = ax1.twinx()  
color = 'tab:orange'
ax2.set_ylabel('Veículos Imobilizados (< 5km/h)', color=color, fontweight='bold')
ax2.plot(penetrations, halting_vehicles_max, marker='^', color=color, linewidth=3, label='Veículos Imobilizados')
ax2.tick_params(axis='y', labelcolor=color)

# --- ALTERAÇÃO DO TÍTULO AQUI ---
plt.title('Mitigação da Onda de Choque: Fila vs. Imobilização', fontweight='bold')
fig.tight_layout()
plt.savefig('grafico_filas.png', dpi=300)
plt.close()

print("\n[SUCESSO] Gráficos atualizados e guardados na diretoria atual (formato .png)!")