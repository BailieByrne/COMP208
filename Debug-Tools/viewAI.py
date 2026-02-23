import pandas as pd
import matplotlib.pyplot as plt

# Read the CSV files
df = pd.read_csv('stock_prices.csv')
df_predicted = pd.read_csv('predicted_prices.csv')
step_size = 1
#Change step size 1 shows full picture
#5 shows every 5th point (default to the player)

plt.figure(figsize=(10, 6))
plt.plot(df['Time'][::step_size], df['Price'][::step_size], linestyle='-', linewidth=1, label='Actual')
plt.plot(df_predicted['Time'][::step_size], df_predicted['Price'][::step_size], linestyle='-', linewidth=1, color='red', label='Predicted')

plt.xlabel('Time')
plt.ylabel('Price')
plt.title('Stock Prices Over Time')
plt.xticks(rotation=45)
plt.legend()
plt.tight_layout()
plt.show()