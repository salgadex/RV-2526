import matplotlib.pyplot as plt

taxas_v2x = [0, 25, 50, 75, 100]
time_loss_p95 = [1050.00, 966.52, 879.58, 964.86, 1706.14]
tempo_viagem_p95 = [1134.00, 1106.00, 1012.65, 1105.00, 1754.50]
veiculos_concluidos = [1765, 1786, 1762, 1901, 1956]

fig, ax1 = plt.subplots(figsize=(12, 7))

color1, color2 = '#d62728', '#9467bd'
ax1.set_xlabel('Taxa de Penetração V2X (%)', fontsize=13, fontweight='bold')
ax1.set_ylabel('Tempo (Segundos)', fontsize=13, fontweight='bold')

ln1 = ax1.plot(taxas_v2x, tempo_viagem_p95, marker='s', linestyle='--', linewidth=2.5, markersize=9, color=color1, label='Tempo Viagem (p95)')
ln2 = ax1.plot(taxas_v2x, time_loss_p95, marker='o', linestyle='-', linewidth=2.5, markersize=9, color=color2, label='TimeLoss (p95)')

# FIX: Posições estáticas absolutas para não haver qualquer colisão
offsets_red = [(0, 12), (0, 12), (0, 12), (0, 12), (0, 15)] # Sempre por cima
offsets_purple = [(0, -18), (0, -18), (0, -18), (0, -18), (0, -18)] # Sempre por baixo

for i, txt in enumerate(tempo_viagem_p95):
    ax1.annotate(f"{txt:.1f}", (taxas_v2x[i], tempo_viagem_p95[i]), textcoords="offset points", xytext=offsets_red[i], ha='center', fontsize=11)

for i, txt in enumerate(time_loss_p95):
    ax1.annotate(f"{txt:.1f}", (taxas_v2x[i], time_loss_p95[i]), textcoords="offset points", xytext=offsets_purple[i], ha='center', fontsize=11)

ax2 = ax1.twinx()
color3 = '#2ca02c'
ax2.set_ylabel('Veículos Concluídos (Throughput)', color=color3, fontsize=13, fontweight='bold')

ln3 = ax2.plot(taxas_v2x, veiculos_concluidos, marker='^', linestyle=':', linewidth=2.5, markersize=10, color=color3, label='Veículos Concluídos')
ax2.tick_params(axis='y', labelcolor=color3)

# FIX: Deslocamentos manuais cirúrgicos para o Throughput
# 0%: empurrado para a esquerda; 25% e 50%: empurrados para cima; 75%: cima; 100%: empurrado para a direita e para baixo
offsets_green = [(-35, 0), (0, 20), (0, 20), (0, 15), (35, -20)]

for i, txt in enumerate(veiculos_concluidos):
    ax2.annotate(f"{txt}", (taxas_v2x[i], veiculos_concluidos[i]), textcoords="offset points", xytext=offsets_green[i], ha='center', fontsize=11, color=color3, fontweight='bold')

ax1.set_xticks(taxas_v2x)
ax1.set_xticklabels([f"{x}%" for x in taxas_v2x], fontsize=11)
ax1.grid(True, linestyle='--', alpha=0.5)
ax1.set_title('Impacto do Filtro Geo-Routing: Tempos Críticos (p95) vs. Escoamento', fontsize=15, fontweight='bold')

lns = ln1 + ln2 + ln3
labs = [l.get_label() for l in lns]
ax1.legend(lns, labs, loc='upper center', bbox_to_anchor=(0.5, -0.12), ncol=3, fontsize=12)

ax1.set_ylim(min(time_loss_p95) * 0.85, max(tempo_viagem_p95) * 1.15)
ax2.set_ylim(min(veiculos_concluidos) * 0.95, max(veiculos_concluidos) * 1.05)

fig.tight_layout()
plt.savefig('grafico_resultados_final_corrigido.png', dpi=300, bbox_inches='tight')
print("Gráfico limpo gerado com sucesso!")