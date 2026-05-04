import matplotlib.pyplot as plt
import numpy as np

taxas_v2x = [0, 25, 50, 75, 100]
time_loss_p95 = [397.04, 370.08, 387.33, 406.58, 479.11]
tempo_viagem_p95 = [503.40, 490.00, 536.40, 514.10, 577.30]
veiculos_concluidos = [867, 861, 493, 867, 867]

fig, ax1 = plt.subplots(figsize=(12, 7))

color1, color2 = '#d62728', '#9467bd'
ax1.set_xlabel('Taxa de Penetração V2X (%)', fontsize=13, fontweight='bold')
ax1.set_ylabel('Tempo (Segundos)', fontsize=13, fontweight='bold')

ln1 = ax1.plot(taxas_v2x, tempo_viagem_p95, marker='s', linestyle='--', linewidth=2.5, markersize=9, color=color1, label='Tempo Viagem (p95)')
ln2 = ax1.plot(taxas_v2x, time_loss_p95, marker='o', linestyle='-', linewidth=2.5, markersize=9, color=color2, label='TimeLoss (p95)')

# Anotações dos tempos (Eixo Esquerdo)
for i, txt in enumerate(time_loss_p95):
    ax1.annotate(f"{txt:.1f}", (taxas_v2x[i], time_loss_p95[i]), textcoords="offset points", xytext=(0, -22), ha='center', fontsize=11)
for i, txt in enumerate(tempo_viagem_p95):
    ax1.annotate(f"{txt:.1f}", (taxas_v2x[i], tempo_viagem_p95[i]), textcoords="offset points", xytext=(0, 12), ha='center', fontsize=11)

ax2 = ax1.twinx()
color3 = '#2ca02c'
ax2.set_ylabel('Veículos Concluídos (Throughput)', color=color3, fontsize=13, fontweight='bold')

ln3 = ax2.plot(taxas_v2x, veiculos_concluidos, marker='^', linestyle=':', linewidth=2.5, markersize=10, color=color3, label='Veículos Concluídos')
ax2.tick_params(axis='y', labelcolor=color3)

# Anotações do throughput (Eixo Direito) - Deslocadas dinamicamente para cima ou para baixo para evitar colisões
for i, txt in enumerate(veiculos_concluidos):
    y_offset = -22 if taxas_v2x[i] == 50 else 15
    ax2.annotate(f"{txt}", (taxas_v2x[i], veiculos_concluidos[i]), textcoords="offset points", xytext=(0, y_offset), ha='center', fontsize=11, color=color3, fontweight='bold')

ax1.set_xticks(taxas_v2x)
ax1.set_xticklabels([f"{x}%" for x in taxas_v2x], fontsize=11)
ax1.grid(True, linestyle='--', alpha=0.5)
ax1.set_title('Evolução do Tráfego: Tempos Críticos vs. Vazão (Throughput)', fontsize=15, fontweight='bold')

lns = ln1 + ln2 + ln3
labs = [l.get_label() for l in lns]
ax1.legend(lns, labs, loc='upper center', bbox_to_anchor=(0.5, -0.12), ncol=3, fontsize=12)

ax1.set_ylim(min(time_loss_p95) * 0.8, max(tempo_viagem_p95) * 1.15)
ax2.set_ylim(400, max(veiculos_concluidos) * 1.1)

fig.tight_layout()
plt.savefig('grafico_completo_corrigido.png', dpi=300, bbox_inches='tight')