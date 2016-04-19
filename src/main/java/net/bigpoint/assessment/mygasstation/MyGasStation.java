package net.bigpoint.assessment.mygasstation;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.ArrayList;

import net.bigpoint.assessment.gasstation.*;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;

public class MyGasStation implements GasStation {
	// all gas pumps
	private ArrayList<GasPump> gasPumps = new ArrayList<GasPump>();
	// remember occupied pumps
	private ArrayList<GasPump> occupiedPumps = new ArrayList<GasPump>();
	// a map of prices for each gas type
	private HashMap<GasType, Double> pricePerType = new HashMap<GasType, Double>();
	// An array list of blocking queues for threads for each pump type, makes sure the that next thread
	// in line is the first one to wait
	private ArrayList<ArrayBlockingQueue<Thread>> threadQueues = new ArrayList<ArrayBlockingQueue<Thread>>();
	
	// tracking of sales and cancellation 
	private int numberOfCancellationsTooExpensive = 0;
	private int numberOfSales = 0;
	private int numberOfCancellationsNoGas = 0;
	private double revenue = 0;
	
	/**
	 * Constructor
	 */
	public MyGasStation() {
		for(GasType type : GasType.values()) {
			threadQueues.add(new ArrayBlockingQueue<Thread>(100));
			setPrice(type, 0);			
		}
	}
	
	public synchronized Collection<GasPump> getGasPumps() {
		return new ArrayList<GasPump>(gasPumps);
	}
	
	/**
	 * Checks if amount in liters is available at any gas pump of the right type 
	 * @param type gas type
	 * @param amountInLiters amount of gas in liters necessary
	 * @return true if a gas pump with the available amount was found
	 */
	private boolean isAmountInLitersAvailable(GasType type, double amountInLiters) {
		for (GasPump pump : gasPumps) {
			// Test each pump for the right type and the available amount
			if (pump.getGasType() == type && Double.compare(pump.getRemainingAmount(), amountInLiters) >= 0) {
				return true;
			}
		}
		return false;
	}
	
	public void addGasPump(GasPump pump) {
		gasPumps.add(pump);		
	}
	
	/**
	 * Trying to find an available pump
	 * @param type type of gas for the pump
	 * @param amountInLiters amount in liters necessary
	 * @return available pump, null if none is available
	 * @throws NotEnoughGasException
	 */
	private synchronized GasPump waitForFreePump(GasType type, double amountInLiters)
			throws NotEnoughGasException {
		GasPump chosenPump = null;
		
		// Check if suitable pumps are available
		if (!isAmountInLitersAvailable(type, amountInLiters)) {
			++numberOfCancellationsNoGas;
			throw new NotEnoughGasException();			
		}
		
		// Find pump with right gas type, and remaining amount that is not occupied
		for (GasPump pump : gasPumps) {
			if (!occupiedPumps.contains(pump) 
					&& pump.getGasType() == type 
					&& pump.getRemainingAmount() >= amountInLiters) {
				chosenPump = pump;
				break;
			}
		}
		
		// If a suitable pump was found, check if it's the current threads turn to pump
		if (chosenPump != null) {
			if (threadQueues.get(type.ordinal()).peek() != null && !threadQueues.get(type.ordinal()).peek().equals(Thread.currentThread())) {
				return null;
			} else if (threadQueues.get(type.ordinal()).peek() != null && threadQueues.get(type.ordinal()).peek().equals(Thread.currentThread())) {
				threadQueues.get(type.ordinal()).poll();
			}
			// occupy pump
			occupiedPumps.add(chosenPump);
		}
		
		return chosenPump;
	}

	public double buyGas(GasType type, double amountInLiters, double maxPricePerLiter)
			throws NotEnoughGasException, GasTooExpensiveException {
		
		// Test if price is not too expensive
		if (Double.compare(maxPricePerLiter, pricePerType.get(type)) < 0) {
			synchronized (this) {
				++numberOfCancellationsTooExpensive;
			}
			throw new GasTooExpensiveException();			
		}
		
		// Loop to wait for an available pump
		GasPump pump = null;
		while (pump == null) {
			pump = waitForFreePump(type, amountInLiters);
			if (pump == null) {
				// Wait and add self to waiting queue
				synchronized(this) {
					try {
						if (!threadQueues.get(type.ordinal()).contains(Thread.currentThread())) {
							threadQueues.get(type.ordinal()).add(Thread.currentThread());
						}
						wait();
					} catch (InterruptedException e) {
						//proceed
					}			
				}
			} else {
				// Pump gas as soon as it is your turn
				pump.pumpGas(amountInLiters);
				double price = 0;
				synchronized (this) {
					// on finish, free occupied pump
					occupiedPumps.remove(pump);
					price = amountInLiters * pricePerType.get(type);
					revenue += price;
					++numberOfSales;
					notifyAll();
				}
				return price;
			}
		}
		
		return 0;
	}

	public synchronized double getRevenue() {
		return revenue;
	}

	public synchronized int getNumberOfSales() {
		return numberOfSales;
	}

	public synchronized int getNumberOfCancellationsNoGas() {
		return numberOfCancellationsNoGas;
	}

	public synchronized int getNumberOfCancellationsTooExpensive() {
		return numberOfCancellationsTooExpensive;
	}

	public synchronized double getPrice(GasType type) {
		return pricePerType.get(type);
	}

	public synchronized void setPrice(GasType type, double price) {
		pricePerType.put(type, price);
	}

}
