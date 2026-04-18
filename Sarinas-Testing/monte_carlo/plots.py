from pathlib import Path
from matplotlib import pyplot as plt 

outer_folder = Path("/Users/sarinasaiyed/COMP208/Sarinas-Testing/outputs")

def creating_plots():
    log_file = outer_folder / "timing_log.txt"

    if not log_file.exists():
        print("No timing logs found")
        return
    
    run_number = []
    times = []
    with open(log_file, 'r') as file:   
        for line in file:
           if line.startswith("Run "): #reading the file and skipping everything that doesnt start w that
               run_nums = line.split()[1].rstrip('Python=')
               time_value = line.split()[2].rstrip("s") #taking out just the time value
               run_number.append(int(run_nums))
               times.append(float(time_value))
            
    if not times:
        print("no data to plot")
        return
    
    #create a folder for the plots 
    plot_file = Path("/Users/sarinasaiyed/COMP208/Sarinas-Testing/outputs/plots")
    plot_file.mkdir(exist_ok=True)


    #remove ai code:
    plt.figure(figsize=(10, 6))
    plt.plot(run_number, times, marker='o', linestyle='-', linewidth=2, markersize=4)
    plt.xlabel('Run Number', fontsize=12)
    plt.ylabel('Runtime (seconds)', fontsize=12)
    plt.title('Monte Carlo Runtime per Run', fontsize=14, fontweight='bold')
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(plot_file/ 'runtime_per_run.png', dpi=300)
    print("Saved: runtime_per_run.png")
    plt.close()

    # remove ai code


if __name__ == "__main__":
    creating_plots()