import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;
import net.bigpoint.assessment.mygasstation.MyGasStation;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;


public class GasStationTest  {
	//private GasStation station;
	
	private final int numGasTypes = GasType.values().length;
	private double amountPumped[] = new double[numGasTypes];
	private double totalRevenue = 0;
	private int numberOfSales = 0;
	private double[] prices = new double[GasType.values().length];
	
	private GasStation setupSmallStation() {
    	for (int i = 0; i < numGasTypes; ++i) {
    		amountPumped[i] = 0;
    	}
    	
    	GasStation station = new MyGasStation();
    	prices[GasType.DIESEL.ordinal()] = 0.98;
    	prices[GasType.SUPER.ordinal()] = 1.21;
    	prices[GasType.REGULAR.ordinal()] = 1.15;
    	station.setPrice(GasType.DIESEL, prices[GasType.DIESEL.ordinal()]);
    	station.setPrice(GasType.SUPER, prices[GasType.SUPER.ordinal()]);
    	station.setPrice(GasType.REGULAR, prices[GasType.REGULAR.ordinal()]);
    	station.addGasPump(new GasPump(GasType.DIESEL, 105.0));
    	station.addGasPump(new GasPump(GasType.SUPER, 135.0));
    	station.addGasPump(new GasPump(GasType.REGULAR, 125.0));
    	return station;
	}
	
	private GasStation setupBigGasStation() {
    	for (int i = 0; i < numGasTypes; ++i) {
    		amountPumped[i] = 0;
    	}
    	
    	GasStation station = new MyGasStation();
    	prices[GasType.DIESEL.ordinal()] = 0.98;
    	prices[GasType.SUPER.ordinal()] = 1.21;
    	prices[GasType.REGULAR.ordinal()] = 1.15;
    	station.setPrice(GasType.DIESEL, prices[GasType.DIESEL.ordinal()]);
    	station.setPrice(GasType.SUPER, prices[GasType.SUPER.ordinal()]);
    	station.setPrice(GasType.REGULAR, prices[GasType.REGULAR.ordinal()]);
    	station.addGasPump(new GasPump(GasType.DIESEL, 105.0));
    	station.addGasPump(new GasPump(GasType.DIESEL, 125.0));
    	station.addGasPump(new GasPump(GasType.DIESEL, 100.0));
    	station.addGasPump(new GasPump(GasType.SUPER, 135.0));
    	station.addGasPump(new GasPump(GasType.SUPER, 115.0));
    	station.addGasPump(new GasPump(GasType.REGULAR, 125.0));
    	return station;
	}

    private void testTooExpensive(double amount, double priceMax, GasType type, GasStation station) {
    	Exception exception = null;
    	try {
    		station.buyGas(type, amount, priceMax);
    	} catch (Exception e) {
    		exception = e;
    	}
    	Assert.assertTrue(exception != null && exception.getClass() == GasTooExpensiveException.class);
    }
    
    private void startThread(final double amount, final double priceMax, final GasType type, final GasStation station) {
//    	try {
//			Thread.sleep(1);
//		} catch (InterruptedException e1) {
//			
//		}
    	( new Thread() {public void run() { 
    			try {
					station.buyGas(type, amount, priceMax);
				} catch (Exception e) {
					e.printStackTrace();
				}
    		} 
		}).start();
    	amountPumped[type.ordinal()] += amount;
    	totalRevenue += station.getPrice(type) * amount;
    	++numberOfSales;
    }

    private void testNotEnoughGas(double amount, double priceMax, GasType type, GasStation station) {
    	Exception exception = null;
    	try {
    		station.buyGas(type, amount, priceMax);
    	} catch (Exception e) {
    		exception = e;
    	}
    	Assert.assertTrue(exception.getClass().toString(), exception != null && exception.getClass() == NotEnoughGasException.class);
    }
    
    private void testThreadedNotEnoughGas(final double amount, final double priceMax, final GasType type, final GasStation station) {
    	( new Thread() {public void run() {
    		boolean caughtRightException = false;
			try {
				station.buyGas(type, amount, priceMax);
			} catch (NotEnoughGasException e) {
				caughtRightException = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
			Assert.assertTrue(caughtRightException);
		}
    	}).start();
    }
    
    private void testThreadedTooExpensive(final double amount, final double priceMax, final GasType type, final GasStation station) {
    	( new Thread() {public void run() {
    		boolean caughtRightException = false;
			try {
				station.buyGas(type, amount, priceMax);
			} catch (GasTooExpensiveException e) {
				caughtRightException = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
			Assert.assertTrue(caughtRightException);
		}
    	}).start();
    }
    
    private void pumpGas(final double amount, final double priceMax, final GasType type, GasStation station) {
    	try {
			station.buyGas(type, amount, priceMax);
		} catch (Exception e) {
			Assert.assertTrue("Pumping Gas should work: "+e.toString(), false);
		}
    	amountPumped[type.ordinal()] += amount;
    	totalRevenue += station.getPrice(type) * amount;
    	++numberOfSales;    	
    }

    @Test
    public final void testTiming() throws Exception {
    	GasStation station = setupBigGasStation();
    	long beforeTime = System.nanoTime();
    	startThread(10, 2, GasType.REGULAR, station);
    	startThread(10, 2, GasType.SUPER, station);
    	pumpGas(10, 2, GasType.DIESEL, station);
    	long afterTime = System.nanoTime();
    	System.out.println("Timing: "+TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime));
    	Assert.assertEquals(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 10 * 100, 10.0);

    	beforeTime = System.nanoTime();
    	startThread(20, 2, GasType.REGULAR, station);
    	startThread(10, 2, GasType.SUPER, station);
    	Thread.sleep(1);
    	pumpGas(10, 2, GasType.REGULAR, station);
    	afterTime = System.nanoTime();
    	System.out.println("Timing: "+TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime));
    	Assert.assertEquals(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 30 * 100, 10.0);

    	// Wait for queues to finish
    	//Thread.sleep(3000);
    	
    	beforeTime = System.nanoTime();
    	startThread(10, 2, GasType.REGULAR, station);
    	startThread(11, 2, GasType.REGULAR, station);
    	startThread(12, 2, GasType.REGULAR, station);
    	Thread.sleep(100);
    	pumpGas(13, 2, GasType.REGULAR, station);
    	afterTime = System.nanoTime();
    	System.out.println("Timing: "+TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime));
    	Assert.assertEquals(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 46 * 100, 10.0);

    	// Wait for queues to finish
    	//Thread.sleep(3000);
    	
    	beforeTime = System.nanoTime();
    	startThread(30, 2, GasType.SUPER, station);
    	startThread(20, 2, GasType.SUPER, station);
    	startThread(20, 2, GasType.SUPER, station);
    	startThread(10, 2, GasType.REGULAR, station);
    	startThread(40, 2, GasType.DIESEL, station);
    	startThread(30, 2, GasType.DIESEL, station);
    	startThread(20, 2, GasType.DIESEL, station);
    	startThread(40, 2, GasType.DIESEL, station);
    	startThread(30, 2, GasType.DIESEL, station);
    	startThread(20, 2, GasType.DIESEL, station);
    	Thread.sleep(100);
    	pumpGas(10, 2, GasType.REGULAR, station); // Wait 10 + 10 (self) Millisecs
    	pumpGas(10, 2, GasType.SUPER, station); // Wait 30 - 20 (previous) = 10 + 10 (self) Millisecs
    	pumpGas(10, 2, GasType.DIESEL, station); // Wait 60 - 40 (previous) + 10 (self) 
    	// total 70 
    	afterTime = System.nanoTime();
    	System.out.println("Timing: "+TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime));
    	Assert.assertEquals(7000, TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 10);
    	
    	//Thread.sleep(3000);
    	// Test if at the end, the expected number of sales and total revenue is available
    	
    	Assert.assertEquals(station.getNumberOfSales(), numberOfSales);
    	Assert.assertEquals(station.getRevenue(), totalRevenue, 0.001);
    	
    	// Test if there's not more than 100 liters of Diesel available
    	testThreadedNotEnoughGas(100, 2, GasType.DIESEL, station);
    }
    
    @Test
    public final void testExceptions() throws Exception {
    	// Test Exceptions
    	GasStation station = setupSmallStation();
    	testTooExpensive(50, 0.97, GasType.DIESEL, station);
    	testTooExpensive(50, 1.20, GasType.SUPER, station);  
    	testTooExpensive(50, 1.14, GasType.REGULAR, station);
    	testNotEnoughGas(500, 0.99, GasType.DIESEL, station);
    	testNotEnoughGas(500, 1.22, GasType.SUPER, station);  
    	Assert.assertEquals(station.getNumberOfCancellationsTooExpensive(), 3);
    	pumpGas(10,  1.16, GasType.REGULAR, station);
    	testNotEnoughGas(115.1, 1.16, GasType.REGULAR, station);
    }
    
    @Test
    public final void testPriceChange() throws Exception {
    	GasStation station = setupSmallStation();

    	long beforeTime = System.nanoTime();
    	startThread(20, 1.15, GasType.REGULAR, station);
    	Thread.sleep(10);
    	// Wait for your turn, realize price change
    	testThreadedTooExpensive(20, 1.15, GasType.REGULAR, station);
    	prices[GasType.REGULAR.ordinal()] = 1.16;
    	// Change price mid pumping
    	station.setPrice(GasType.REGULAR, prices[GasType.REGULAR.ordinal()]);
    	Thread.sleep(10);
    	testThreadedTooExpensive(20, 1.15, GasType.REGULAR, station);
    	Thread.sleep(10);
    	startThread(20, 1.16, GasType.REGULAR, station);
    	Thread.sleep(10);
    	pumpGas(20, 1.16, GasType.REGULAR, station);
    	long afterTime = System.nanoTime();
    	
    	Assert.assertEquals(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 60 * 100, 10.0);
    	Assert.assertEquals(station.getRevenue(), 20*1.15+20*1.16+20*1.16, 0.001);	
    }
    
    @Test
    public final void testModifyPumps() throws Exception {
    	GasStation station = setupSmallStation();

    	startThread(20, 1.15, GasType.REGULAR, station);
    	Thread.sleep(10);
    	Collection<GasPump> pumps = station.getGasPumps();
    	// Empty copied pumps
    	for (GasPump pump : pumps) {
    		pump.pumpGas(pump.getRemainingAmount());
    	}
    	startThread(20, 1.15, GasType.REGULAR, station);
    	// Clear pumps
    	pumps.clear();
    	pumpGas(20, 1.15, GasType.REGULAR, station);
    }
    
    @Test
    public final void testAddPump() throws Exception {
    	GasStation station = setupSmallStation();

    	long beforeTime = System.nanoTime();
    	startThread(50, 1.15, GasType.REGULAR, station);
    	startThread(50, 1.15, GasType.REGULAR, station);
    	Thread.sleep(10);
    	// Add Pump mid pumping
    	station.addGasPump(new GasPump(GasType.REGULAR, 50));
    	Thread.sleep(10);
    	pumpGas(10, 1.15, GasType.REGULAR, station);
    	long afterTime = System.nanoTime();
    	Assert.assertEquals(60 * 100, TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 10.0);
    }
}
