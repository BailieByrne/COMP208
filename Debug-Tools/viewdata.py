import pandas as pd

import matplotlib.pyplot as plt

# Read the CSV file
df = pd.read_csv('stock_prices.csv')
step_size = 5
#Change step size 1 shows full picture
#5 shows every 5th point (default to the player)

plt.figure(figsize=(10, 6))
plt.plot(df['Time'][::step_size], df['Price'][::step_size], linestyle='-', linewidth=1)

plt.xlabel('Time')
plt.ylabel('Price')
plt.title('Stock Prices Over Time')
plt.xticks(rotation=45)
plt.tight_layout()
plt.show()