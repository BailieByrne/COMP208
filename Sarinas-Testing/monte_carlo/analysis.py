from pathlib import Path
import statistics
from datetime import datetime

outer_folder = Path("/Users/sarinasaiyed/COMP208/Sarinas-Testing/outputs")

def analysis():
    log_file = outer_folder / "timing_log.txt"
    
    if not log_file.exists():
        print("No logs found")
        return
    
    #storing differnt times
    python_times = []
    ccp_total = []
    sim_times = []
    ai_times = []

    with open(log_file, 'r') as file:   
        for line in file:
           if line.startswith("Run "): #reading the file and skipping everything that doesnt start w that
            line = line.strip()
            python_string = line.split("Python=")[1].split("s")[0].strip() #taking out just the time value
            python_times.append(float(python_string))
            ccp_string = line.split("C++ Total=")[1].split("s")[0].strip()
            ccp_total.append(float(ccp_string))
            sim_string = line.split("Sim=")[1].split("s")[0].strip() 
            sim_times.append(float(sim_string))
            ai_string = line.split("AI=")[1].split("s")[0].strip()
            ai_times.append(float(ai_string))

    if not python_times:
        print("No Python timing data found")
        return
    

    #calculating the anyasis stats
    def analysis_stats(times):
        return {
            "average": statistics.mean(times),
            "min": min(times),
            "max": max(times),
            "variance": statistics.variance(times) if len(times) > 1 else 0,
            "std_dev": statistics.stdev(times) if len(times) > 1 else 0
        }
    
    python_stats = analysis_stats(python_times)
    ccp_stats = analysis_stats(ccp_total)
    sim_stats = analysis_stats(sim_times)
    ai_stats = analysis_stats(ai_times)

    
    #printing the results
    print("=" * 50)
    print("Monte Carlo Timing Analysis:")
    print("=" * 50)

    print("Python Timing Analysis:")
    print(f"Average time: {python_stats['average']:.2f} seconds")
    print(f"Min time: {python_stats['min']:.2f} seconds")
    print(f"Max time: {python_stats['max']:.2f} seconds")
    print(f"Standard Deviation: {python_stats['std_dev']:.6f} seconds")
    print(f"Variance: {python_stats['variance']:.6f} seconds^2") 

    print("\nC++ Total Timing Analysis:")
    print(f"Average time: {ccp_stats['average']:.2f} seconds")
    print(f"Min time: {ccp_stats['min']:.2f} seconds")
    print(f"Max time: {ccp_stats['max']:.2f} seconds")
    print(f"Standard Deviation: {ccp_stats['std_dev']:.6f} seconds")
    print(f"Variance: {ccp_stats['variance']:.6f} seconds^2") 

    print("\nSimulation Timing Analysis:")
    print(f"Average time: {sim_stats['average']:.2f} seconds")
    print(f"Min time: {sim_stats['min']:.2f} seconds")
    print(f"Max time: {sim_stats['max']:.2f} seconds")
    print(f"Standard Deviation: {sim_stats['std_dev']:.6f} seconds")
    print(f"Variance: {sim_stats['variance']:.8f} seconds^2")  

    print("\nAI Timing Analysis:")
    print(f"Average time: {ai_stats['average']:.2f} seconds")
    print(f"Min time: {ai_stats['min']:.2f} seconds")
    print(f"Max time: {ai_stats['max']:.2f} seconds")
    print(f"Standard Deviation: {ai_stats['std_dev']:.6f} seconds")
    print(f"Variance: {ai_stats['variance']:.6f} seconds^2")    

    sim_precentage = (sim_stats['average'] / ccp_stats['average']) * 100 if ccp_stats['average'] > 0 else 0
    ai_precentage = (ai_stats['average'] / ccp_stats['average']) * 100 if ccp_stats['average'] > 0 else 0
    print(f"\nSimulation accounts for {sim_precentage:.2f}% of total C++ time")
    print(f"AI accounts for {ai_precentage:.2f}% of total C++ time")



    

    summary_file = Path("/Users/sarinasaiyed/COMP208/Sarinas-Testing/outputs/summary.txt")
    with open(summary_file, 'w') as sumry:
        sumry.write(f"MC test run: {datetime.now()} \n") 
        sumry.write("=" * 60 + "\n\n")

        sumry.write("Python Timing Analysis:\n")
        sumry.write(f"Average time: {python_stats['average']:.2f} seconds\n")
        sumry.write(f"Min time: {python_stats['min']:.2f} seconds\n")
        sumry.write(f"Max time: {python_stats['max']:.2f} seconds\n")
        sumry.write(f"Standard Deviation: {python_stats['std_dev']:.6f} seconds\n")
        sumry.write(f"Variance: {python_stats['variance']:.6f} seconds^2\n\n")

        sumry.write("C++ Total Timing Analysis:\n")
        sumry.write(f"Average time: {ccp_stats['average']:.2f} seconds\n")
        sumry.write(f"Min time: {ccp_stats['min']:.2f} seconds\n")
        sumry.write(f"Max time: {ccp_stats['max']:.2f} seconds\n")
        sumry.write(f"Standard Deviation: {ccp_stats['std_dev']:.6f} seconds\n")
        sumry.write(f"Variance: {ccp_stats['variance']:.6f} seconds^2\n\n")

        sumry.write("Simulation Timing Analysis:\n")
        sumry.write(f"Average time: {sim_stats['average']:.2f} seconds\n")
        sumry.write(f"Min time: {sim_stats['min']:.2f} seconds\n")
        sumry.write(f"Max time: {sim_stats['max']:.2f} seconds\n")
        sumry.write(f"Standard Deviation: {sim_stats['std_dev']:.6f} seconds\n")
        sumry.write(f"Variance: {sim_stats['variance']:.8f} seconds^2\n\n")

        sumry.write("AI Timing Analysis:\n")
        sumry.write(f"Average time: {ai_stats['average']:.2f} seconds\n")
        sumry.write(f"Min time: {ai_stats['min']:.2f} seconds\n")
        sumry.write(f"Max time: {ai_stats['max']:.2f} seconds\n")
        sumry.write(f"Standard Deviation: {ai_stats['std_dev']:.6f} seconds\n")
        sumry.write(f"Variance: {ai_stats['variance']:.6f} seconds^2\n\n")

        sumry.write(f"Simulation accounts for {sim_precentage:.2f}% of total C++ time\n")
        sumry.write(f"AI accounts for {ai_precentage:.2f}% of total C++ time\n")
    print(f"\nSummary written to {summary_file}")   

if __name__ == "__main__":
    analysis()
               
            
