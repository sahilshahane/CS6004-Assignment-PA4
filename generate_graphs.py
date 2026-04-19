import os
import glob
import csv
import matplotlib.pyplot as plt
import numpy as np

PERF_DIR = "perf_result"
GRAPHS_DIR = "graphs"

def parse_perf_csv(filepath):
    metrics = {}
    if not os.path.exists(filepath):
        return metrics
    with open(filepath, 'r') as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) < 3:
                continue
            
            # First element is usually the count
            value_str = row[0].replace(',', '')
            event = row[2].strip()
            
            try:
                value = float(value_str)
            except ValueError:
                continue
                
            metrics[event] = value
            
            # Extract IPC and branch miss rate from the 6th column (index 5)
            if event == 'instructions':
                try:
                    if len(row) > 5 and row[5].strip():
                        metrics['ipc'] = float(row[5])
                except ValueError:
                    pass
            elif event == 'branch-misses':
                try:
                    if len(row) > 5 and row[5].strip():
                        metrics['branch_miss_rate'] = float(row[5])
                except ValueError:
                    pass
            elif event == 'task-clock':
                metrics['execution_time_ms'] = value
    return metrics

def parse_invoke_metrics(filepath):
    metrics = {'Static Calls': 0, 'Instance Calls': 0}
    if not os.path.exists(filepath):
        return metrics
    
    with open(filepath, 'r') as f:
        for line in f:
            if line.startswith('Static Calls:'):
                metrics['Static Calls'] = int(line.split(':')[1].strip())
            elif line.startswith('Instance Calls:'):
                metrics['Instance Calls'] = int(line.split(':')[1].strip())
    return metrics

def plot_comparison(test_case, metric_filename, title, y_label, baseline_val, optimized_val):
    out_dir = os.path.join(GRAPHS_DIR, test_case)
    os.makedirs(out_dir, exist_ok=True)
    
    labels = ['Baseline', 'Optimized']
    values = [baseline_val, optimized_val]
    
    # Handle None values
    values = [v if v is not None else 0 for v in values]
    
    plt.figure(figsize=(6, 4))
    bars = plt.bar(labels, values, color=['#d9534f', '#5cb85c'], width=0.5)
    plt.title(title)
    plt.ylabel(y_label)
    
    for bar in bars:
        yval = bar.get_height()
        display_val = f"{yval:.5f}" if isinstance(yval, float) and not yval.is_integer() else str(int(yval))
        plt.text(bar.get_x() + bar.get_width()/2, yval, display_val, ha='center', va='bottom')
        
    plt.margins(y=0.15)
    plt.tight_layout()
    plt.savefig(os.path.join(out_dir, f"{metric_filename}.png"))
    plt.close()

def plot_grouped_bar(test_case, metric_filename, title, labels, baseline_vals, optimized_vals):
    out_dir = os.path.join(GRAPHS_DIR, test_case)
    os.makedirs(out_dir, exist_ok=True)
    
    x = np.arange(len(labels))
    width = 0.35
    
    fig, ax = plt.subplots(figsize=(7, 5))
    rects1 = ax.bar(x - width/2, baseline_vals, width, label='Baseline', color='#d9534f')
    rects2 = ax.bar(x + width/2, optimized_vals, width, label='Optimized', color='#5cb85c')
    
    ax.set_ylabel('Count')
    ax.set_title(title)
    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    ax.legend()
    
    def autolabel(rects):
        for rect in rects:
            height = rect.get_height()
            ax.annotate(f'{int(height)}',
                        xy=(rect.get_x() + rect.get_width() / 2, height),
                        xytext=(0, 3),  # 3 points vertical offset
                        textcoords="offset points",
                        ha='center', va='bottom')
            
    autolabel(rects1)
    autolabel(rects2)
    
    fig.tight_layout()
    plt.savefig(os.path.join(out_dir, f"{metric_filename}.png"))
    plt.close()

def main():
    if not os.path.exists(PERF_DIR):
        print(f"Directory {PERF_DIR} not found.")
        return

    test_cases = [d for d in os.listdir(PERF_DIR) if os.path.isdir(os.path.join(PERF_DIR, d))]
    
    for test_case in test_cases:
        tc_dir = os.path.join(PERF_DIR, test_case)
        
        # Find latest perf files
        baseline_perfs = sorted(glob.glob(os.path.join(tc_dir, "baseline_perf_results_*.txt")))
        optimized_perfs = sorted(glob.glob(os.path.join(tc_dir, "optimized_perf_results_*.txt")))
        
        # Find latest invoke metrics
        baseline_invokes = sorted(glob.glob(os.path.join(tc_dir, "baseline_invoke_metrics_*.txt")))
        optimized_invokes = sorted(glob.glob(os.path.join(tc_dir, "optimized_invoke_metrics_*.txt")))
        
        if not (baseline_perfs and optimized_perfs):
            print(f"Skipping {test_case}: Missing perf results")
            continue
            
        b_perf = parse_perf_csv(baseline_perfs[-1])
        o_perf = parse_perf_csv(optimized_perfs[-1])
        
        b_invoke = parse_invoke_metrics(baseline_invokes[-1]) if baseline_invokes else {'Static Calls': 0, 'Instance Calls': 0}
        o_invoke = parse_invoke_metrics(optimized_invokes[-1]) if optimized_invokes else {'Static Calls': 0, 'Instance Calls': 0}

        print(f"Generating graphs for {test_case}...")

        # 1. Branch Miss Rate
        plot_comparison(test_case, "branch_miss_rate", "Branch Miss Rate (%)", "Miss Rate (%)", 
                        b_perf.get('branch_miss_rate', 0), o_perf.get('branch_miss_rate', 0))
        
        # 2. No. of branches
        plot_comparison(test_case, "total_branches", "Total Branches", "Count", 
                        b_perf.get('branches', 0), o_perf.get('branches', 0))
        
        # 3. Static & Instance Calls
        plot_grouped_bar(test_case, "invoke_calls", "Static and Instance Calls", 
                         ['Static Calls', 'Instance Calls'], 
                         [b_invoke['Static Calls'], b_invoke['Instance Calls']],
                         [o_invoke['Static Calls'], o_invoke['Instance Calls']])
        
        # 4. L1 Data Cache Miss
        plot_comparison(test_case, "l1_dcache_misses", "L1 Data Cache Misses", "Misses", 
                        b_perf.get('L1-dcache-load-misses', 0), o_perf.get('L1-dcache-load-misses', 0))
        
        # 5. L1 Instruction Cache Miss
        plot_comparison(test_case, "l1_icache_misses", "L1 Instruction Cache Misses", "Misses", 
                        b_perf.get('L1-icache-load-misses', 0), o_perf.get('L1-icache-load-misses', 0))
        
        # 6. L2 Cache Miss (Perf records LLC for L3, L2 missing by default so plotting LLC as L3)
        plot_comparison(test_case, "l3_cache_misses", "L3 Caches Misses (LLC)", "Misses", 
                        b_perf.get('LLC-load-misses', 0), o_perf.get('LLC-load-misses', 0))
        
        # 7. IPC
        plot_comparison(test_case, "ipc", "Instructions Per Cycle (IPC)", "IPC", 
                        b_perf.get('ipc', 0), o_perf.get('ipc', 0))
        
        # 8. Total Instructions
        plot_comparison(test_case, "total_instructions", "Total Instructions Executed", "Count", 
                        b_perf.get('instructions', 0), o_perf.get('instructions', 0))

        # 9. Execution Speedup
        b_time = b_perf.get('task-clock', 0)
        o_time = o_perf.get('task-clock', 0)
        speedup = (b_time / o_time) if o_time > 0 else 0
        plot_comparison(test_case, "speedup", "Execution Speedup", "Speedup (Baseline=1.0x)", 1.0, speedup)

    print(f"Successfully generated all graphs in ./{GRAPHS_DIR}/")

if __name__ == "__main__":
    main()