
import pandas as pd
import matplotlib.pyplot as plt
import os


def plot_stock_data(filename, step_size=1, label=None, color=None):
	if not os.path.exists(filename):
		print(f"File not found: {filename}")
		return None
	df = pd.read_csv(filename)
	plt.plot(df['Time'][::step_size], df['Price'][::step_size], linestyle='-', linewidth=1, label=label, color=color)
	return df


import glob

def find_tickers():
	stock_files = glob.glob("*_stock_prices.csv")
	predicted_files = glob.glob("*_predicted_prices.csv")
	tickers = set()
	for fname in stock_files:
		if fname.endswith("_stock_prices.csv"):
			tickers.add(fname[:-len("_stock_prices.csv")])
	for fname in predicted_files:
		if fname.endswith("_predicted_prices.csv"):
			tickers.add(fname[:-len("_predicted_prices.csv")])
	return sorted(tickers)


def main():
	while True:
		tickers = find_tickers()
		if not tickers:
			print("No ticker files found in the current directory.")
			break
		print("Available tickers:")
		for idx, ticker in enumerate(tickers, 1):
			print(f"{idx}. {ticker}")
		print(f"{len(tickers)+1}. Exit")
		try:
			t_choice = int(input(f"Select a ticker (1-{len(tickers)+1}): ").strip())
		except ValueError:
			print("Invalid input. Please enter a number.")
			continue
		if t_choice == len(tickers)+1:
			print("Exiting.")
			break
		if not (1 <= t_choice <= len(tickers)):
			print("Invalid choice. Please try again.")
			continue
		ticker = tickers[t_choice-1]
		#Choicee menu for each dataset 
		print(f"Selected ticker: {ticker}")
		print("Select data to view:")
		print("1. Actual prices")
		print("2. Predicted prices")
		print("3. Overlay actual & predicted")
		print("4. Back to ticker selection")
		choice = input("Enter your choice (1/2/3/4): ").strip()
		if choice == '1':
			filename = f"{ticker}_stock_prices.csv"
			plt.figure(figsize=(10, 6))
			plot_stock_data(filename, label='Actual', color=None)
			plt.xlabel('Time')
			plt.ylabel('Price')
			plt.title(f"{ticker} Stock Prices Over Time")
			plt.xticks(rotation=45)
			plt.tight_layout()
			plt.legend()
			plt.show()
		elif choice == '2':
			filename = f"{ticker}_predicted_prices.csv"
			plt.figure(figsize=(10, 6))
			plot_stock_data(filename, label='Predicted', color='red')
			plt.xlabel('Time')
			plt.ylabel('Price')
			plt.title(f"{ticker} Predicted Prices Over Time")
			plt.xticks(rotation=45)
			plt.tight_layout()
			plt.legend()
			plt.show()
		elif choice == '3':
			actual_file = f"{ticker}_stock_prices.csv"
			predicted_file = f"{ticker}_predicted_prices.csv"
			plt.figure(figsize=(10, 6))
			actual_exists = os.path.exists(actual_file)
			predicted_exists = os.path.exists(predicted_file)
			if not actual_exists and not predicted_exists:
				print("Neither actual nor predicted price file found for this ticker.")
				continue
			if actual_exists:
				plot_stock_data(actual_file, label='Actual', color=None)
			if predicted_exists:
				plot_stock_data(predicted_file, label='Predicted', color='red')
			plt.xlabel('Time')
			plt.ylabel('Price')
			plt.title(f"{ticker} Actual vs Predicted Prices")
			plt.xticks(rotation=45)
			plt.tight_layout()
			plt.legend()
			plt.show()
		elif choice == '4':
			continue
		else:
			print("Invalid choice. Please try again.")

#main loop
if __name__ == "__main__":
	main()