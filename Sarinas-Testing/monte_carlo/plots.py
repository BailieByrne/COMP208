from pathlib import Path
from matplotlib import pyplot as plt 
import numpy as np  

outer_folder = Path("/Users/sarinasaiyed/COMP208/Sarinas-Testing/outputs")

def creating_plots():
    log_file = outer_folder / "timing_log.txt"

    if not log_file.exists():
        print("No timing logs found")
        return
    
    run_number = []
    python_times = []
    cpp_total = [] 
    sim_times = []   
    ai_times = []
    
    with open(log_file, 'r') as file:   
        for line in file:
           if line.startswith("Run "): #reading the file and skipping everything that doesnt start w that
               line = line.strip()
               run_num = int(line.split("Run ")[1].split(":")[0].strip())
               run_number.append(run_num)

               python_string = line.split("Python=")[1].split("s")[0].strip() #taking out just the time value
               python_times.append(float(python_string))

               ccp_string = line.split("C++ Total=")[1].split("s")[0].strip()
               cpp_total.append(float(ccp_string))

               sim_string = line.split("Sim=")[1].split("s")[0].strip() 
               sim_times.append(float(sim_string))

               ai_string = line.split("AI=")[1].split("s")[0].strip()
               ai_times.append(float(ai_string))    
            
    if not python_times:
        print("no data to plot")
        return
    
    #create a folder for the plots 
    plot_file = Path(outer_folder / "plots")
    plot_file.mkdir(exist_ok=True)


   #calculate average for pie chart
    avg_sim_time = np.mean(sim_times)
    avg_ai_time = np.mean(ai_times)
    avg_python_time = np.mean(python_times)
    avg_cpp_time = np.mean(cpp_total)

    io_overhead = avg_python_time - avg_cpp_time

    print("=" * 50)
    print("creating plots")
    print("=" * 50)

    #component breakdown pie chart
    labels = ['Monte Carlo\nSimulation', 'AI\nPredictions', 'I/O +\nOverhead']
    sizes = [avg_sim_time, avg_ai_time, io_overhead]
    colors = ['#ff9999','#66b3ff','#99ff99']
    plt.figure(figsize=(8, 8))
    plt.pie(sizes, labels=labels, colors=colors, autopct='%1.1f%%', startangle=140)
    plt.title('Runtime Breakdown: MC Simulation vs AI vs I/O')
    plt.axis('equal')
    

    legend_labels = [ "Monte Carlo Simulation:{:.6f}s".format(avg_sim_time), 
              "AI Predictions:{:.4f}s".format(avg_ai_time), 
              "I/O + Overhead:{:.4f}s".format(io_overhead)]
    
    plt.legend(legend_labels, loc='upper right',bbox_to_anchor=(1.1, 1 ))
    plt.tight_layout()
    plt.savefig(plot_file / "pie_chart.png")
    print("Saved pie_chart.png")

    plt.close()     

    #box plot
    plt.figure(figsize=(10, 6))
    data_plot = [python_times, cpp_total, sim_times, ai_times]
    bp = plt.boxplot(data_plot, tick_labels=['Python Total', 'C++ Total', 'MC Simulation', 'AI Predictions'],
                     patch_artist=True, 
                     showmeans=True, 
                     showfliers=True,
                     boxprops=dict(facecolor='#ff9999', color='black', alpha=0.7),
                        medianprops=dict(color='black', linewidth=2),
                        whiskerprops=dict(color='black', linewidth=1.5),
                        capprops=dict(color='black', linewidth=1.5),
    )

   

    colour_box = [ '#ffcc99',"#ff65ff",'#ff9999', '#66b3ff']
    for patch, color in zip(bp['boxes'], colour_box):
        patch.set_facecolor(color)
        patch.set_alpha(0.7)
    

    plt.title('Timing Distribution for Monte Carlo Simulation')
    plt.ylabel('Runtime (seconds)')
    plt.grid(axis='y')
    plt.tight_layout()
    plt.savefig(plot_file / "box_plot.png")
    print("Saved box_plot.png")
    plt.close()




if __name__ == "__main__":
    creating_plots()