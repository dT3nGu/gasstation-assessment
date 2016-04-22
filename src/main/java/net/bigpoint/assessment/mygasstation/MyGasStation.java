package net.bigpoint.assessment.mygasstation;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.function.DoubleBinaryOperator;

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
	private ConcurrentHashMap<GasType, Double> pricePerType = new ConcurrentHashMap<GasType, Double>();
	// An array list of blocking queues for threads for each pump type, makes sure the that next thread
	// in line is the first one to wait
	private ArrayList<ArrayBlockingQueue<Thread>> threadQueues = new ArrayList<ArrayBlockingQueue<Thread>>();
	
	// tracking of sales and cancellation 
	private AtomicInteger numberOfCancellationsTooExpensive = new AtomicInteger();
	private AtomicInteger numberOfSales = new AtomicInteger();
	private AtomicInteger numberOfCancellationsNoGas = new AtomicInteger();
	
	// Monitor for using pumps
	private Object pumpMonitor = new Object();
	
	//For debugging information
	private long nanoStartTime = 0;
	
	private DoubleAccumulator revenue = new DoubleAccumulator(new DoubleBinaryOperator()
	{
	    public double applyAsDouble( double x, double y )
	    {
	        return x + y;
	    }
	}, 0);
	
	/**
	 * Constructor
	 */
	public MyGasStation() {
		nanoStartTime = System.nanoTime();
		for(GasType type : GasType.values()) {
			threadQueues.add(new ArrayBlockingQueue<Thread>(100));
			setPrice(type, 0);			
		}
	}
	
	public Collection<GasPump> getGasPumps() {
		ArrayList<GasPump> pumps = new ArrayList<GasPump>();
		// Create identical copies of the existing pumps to prevent modification
		synchronized (pumpMonitor) {
			for (GasPump pump : gasPumps) {
				GasPump newPump = new GasPump(pump.getGasType(), pump.getRemainingAmount());
				pumps.add(newPump);
			}
		}
		return pumps;
	}
	
	/**
	 * Checks if amount in liters is available at any gas pump of the right type 
	 * @param type gas type
	 * @param amountInLiters amount of gas in liters necessary
	 * @return true if a gas pump with the available amount was found
	 */
	private boolean isAmountInLitersAvailable(GasType type, double amountInLiters) {
		synchronized (pumpMonitor) {
			for (GasPump pump : gasPumps) {
				// Test each pump for the right type and the available amount
				if (pump.getGasType() == type && Double.compare(pump.getRemainingAmount(), amountInLiters) >= 0) {
					return true;
				}
			}
		}
		return false;
	}
	
	public void addGasPump(GasPump pump) {
		synchronized (pumpMonitor) {
			gasPumps.add(pump);
			// Notify all Threads in case one is waiting for a pump of that type
			pumpMonitor.notifyAll();
		}
	}
	
	/**
	 * Trying to find an available pump
	 * @param type type of gas for the pump
	 * @param amountInLiters amount in liters necessary
	 * @return available pump, null if none is available
	 * @throws NotEnoughGasException
	 */
	private GasPump waitForFreePump(GasType type, double amountInLiters)
			throws NotEnoughGasException {
		GasPump chosenPump = null;
		
		// Check if suitable pumps are available
		if (!isAmountInLitersAvailable(type, amountInLiters)) {
			numberOfCancellationsNoGas.incrementAndGet();
			throw new NotEnoughGasException();			
		}
		
		synchronized (pumpMonitor) {
			// Find pump with right gas type, and remaining amount that is not occupied
			for (GasPump pump : gasPumps) {
				if (!occupiedPumps.contains(pump) 
						&& pump.getGasType() == type 
						&& pump.getRemainingAmount() >= amountInLiters) {
					chosenPump = pump;
					break;
				}
			}
			if (chosenPump != null) {
				// If a suitable pump was found, check if it's the current threads turn to pump
				if (threadQueues.get(type.ordinal()).peek() != null && !threadQueues.get(type.ordinal()).peek().equals(Thread.currentThread())) {
					// Return if it's not your turn
					return null;
				} else if (threadQueues.get(type.ordinal()).peek() != null && threadQueues.get(type.ordinal()).peek().equals(Thread.currentThread())) {
					threadQueues.get(type.ordinal()).poll();
					//System.out.println("Polling " + threadQueues.get(type.ordinal()).poll() + " " +amountInLiters+ " " + type  + "@" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoStartTime));
				}
				//System.out.println("Pumpin " + Thread.currentThread() + " " +amountInLiters+ " " + type  + "@" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoStartTime));
			}
			// occupy pump
			occupiedPumps.add(chosenPump);
		}		
		return chosenPump;
	}
	
	private boolean checkTooExpensive(GasType type, double amountInLiters, double maxPricePerLiter) {
		if (Double.compare(maxPricePerLiter, pricePerType.get(type)) < 0) {
			numberOfCancellationsTooExpensive.getAndIncrement();
			return true;
		}
		return false;
	}
	
	private void freePump(GasPump pump) {
		synchronized(pumpMonitor) {
			// on finish, free occupied pump
			//System.out.println("Finish " + Thread.currentThread() + " " +amountInLiters+ " " + type  + "@" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoStartTime));
			occupiedPumps.remove(pump);
			pumpMonitor.notifyAll();
		}
	}

	public double buyGas(GasType type, double amountInLiters, double maxPricePerLiter)
			throws NotEnoughGasException, GasTooExpensiveException {
		//System.out.println(Thread.currentThread()+" wants "+ amountInLiters+ " " +type  + "@" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoStartTime));
		// pre-check if it's too expensive
		if (checkTooExpensive(type, amountInLiters, maxPricePerLiter)) {
			throw new GasTooExpensiveException();		
		}
		// Loop to wait for an available pump
		GasPump pump = null;
		while (pump == null) {
			pump = waitForFreePump(type, amountInLiters);
			if (pump == null) {
				// Wait and add self to waiting queue
				synchronized(pumpMonitor) {
					try {
						if (!threadQueues.get(type.ordinal()).contains(Thread.currentThread())) {
							threadQueues.get(type.ordinal()).add(Thread.currentThread());
						}
						pumpMonitor.wait();
					} catch (InterruptedException e) {
						//proceed
						//System.out.println("Got notified " + Thread.currentThread() + " " +amountInLiters+ " " + type  + "@" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nanoStartTime));
					}			
				}
			} else {
				// Book price and amount before pumping
				double price = 0;
				//Check again if it's still not too expensive
				if (!checkTooExpensive(type, amountInLiters, maxPricePerLiter)) {
					// charge guaranteed price
					price = amountInLiters * pricePerType.get(type);
					revenue.accumulate(price);
					numberOfSales.incrementAndGet();
					// Pump gas as soon as it is your turn
					pump.pumpGas(amountInLiters);
					freePump(pump);
				} else {
					freePump(pump);
					throw new GasTooExpensiveException();
				}
				
				return price;
			}
		}
		
		return 0;
	}

	public double getRevenue() {
		return revenue.get();
	}

	public int getNumberOfSales() {
		return numberOfSales.get();
	}

	public int getNumberOfCancellationsNoGas() {
		return numberOfCancellationsNoGas.get();
	}

	public int getNumberOfCancellationsTooExpensive() {
		return numberOfCancellationsTooExpensive.get();
	}

	public double getPrice(GasType type) {
		return pricePerType.get(type);
	}

	public void setPrice(GasType type, double price) {
		pricePerType.put(type, price);
	}

}
