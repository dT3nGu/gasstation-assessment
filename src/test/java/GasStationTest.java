import net.bigpoint.assessment.gasstation.GasPump;
import net.bigpoint.assessment.gasstation.GasType;
import net.bigpoint.assessment.gasstation.GasStation;
import net.bigpoint.assessment.gasstation.exceptions.GasTooExpensiveException;
import net.bigpoint.assessment.gasstation.exceptions.NotEnoughGasException;
import net.bigpoint.assessment.mygasstation.MyGasStation;

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class GasStationTest  {
	private GasStation station;
	
	final int numGasTypes = GasType.values().length;
	private double amountPumped[] = new double[numGasTypes];
	private double totalRevenue = 0;
	private int numberOfSales = 0;
    
    @Before
    public void setUp() throws Exception {
    	for (int i = 0; i < numGasTypes; ++i) {
    		amountPumped[i] = 0;
    	}
    	
    	station = new MyGasStation();
    	station.setPrice(GasType.DIESEL, 0.98);
    	station.setPrice(GasType.SUPER, 1.21);
    	station.setPrice(GasType.REGULAR, 1.15);
    	station.addGasPump(new GasPump(GasType.DIESEL, 105.0));
    	station.addGasPump(new GasPump(GasType.DIESEL, 125.0));
    	station.addGasPump(new GasPump(GasType.DIESEL, 55.0));
    	station.addGasPump(new GasPump(GasType.SUPER, 135.0));
    	station.addGasPump(new GasPump(GasType.SUPER, 115.0));
    	station.addGasPump(new GasPump(GasType.REGULAR, 125.0));
    }

    @After
    public void tearDown() throws Exception {
    
    }

    private void testTooExpensive(double amount, double priceMax, GasType type) {
    	Exception exception = null;
    	try {
    		station.buyGas(type, amount, priceMax);
    	} catch (Exception e) {
    		exception = e;
    	}
    	Assert.assertTrue(exception != null && exception.getClass() == GasTooExpensiveException.class);
    }
    
    private void startThread(final double amount, final double priceMax, final GasType type) {
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

    private void testNotEnoughGas(double amount, double priceMax, GasType type) {
    	Exception exception = null;
    	try {
    		station.buyGas(type, amount, priceMax);
    	} catch (Exception e) {
    		exception = e;
    	}
    	Assert.assertTrue(exception.getClass().toString(), exception != null && exception.getClass() == NotEnoughGasException.class);
    }
    
    private void testThreadedNotEnoughGas(final double amount, final double priceMax, final GasType type) {
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
    
    private void pumpGas(final double amount, final double priceMax, final GasType type) {
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
    public final void testBuying() throws Exception {
    	// Test Exceptions
    	testTooExpensive(50, 0.97, GasType.DIESEL);
    	testTooExpensive(50, 1.20, GasType.SUPER);  
    	testTooExpensive(50, 1.14, GasType.REGULAR);
    	testNotEnoughGas(500, 0.99, GasType.DIESEL);
    	testNotEnoughGas(500, 1.22, GasType.SUPER);  
    	Assert.assertEquals(station.getNumberOfCancellationsTooExpensive(), 3);
    	pumpGas(10,  1.16, GasType.REGULAR);
    	testNotEnoughGas(115.1, 1.16, GasType.REGULAR);
    	
    	// Test Timing and queuing 
    	
    	long beforeTime = System.nanoTime();
    	startThread(10, 2, GasType.REGULAR);
    	startThread(10, 2, GasType.SUPER);
    	pumpGas(10, 2, GasType.DIESEL);
    	long afterTime = System.nanoTime();
    	System.out.println(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime));
    	Assert.assertEquals(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 10 * 100, 10.0);

    	beforeTime = System.nanoTime();
    	startThread(20, 2, GasType.REGULAR);
    	startThread(10, 2, GasType.SUPER);
    	Thread.sleep(1);
    	pumpGas(10, 2, GasType.REGULAR);
    	afterTime = System.nanoTime();
    	System.out.println(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime));
    	Assert.assertEquals(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 30 * 100, 10.0);

    	// Wait for queues to finish
    	Thread.sleep(3000);
    	
    	beforeTime = System.nanoTime();
    	startThread(10, 2, GasType.REGULAR);
    	startThread(11, 2, GasType.REGULAR);
    	startThread(12, 2, GasType.REGULAR);
    	Thread.sleep(100);
    	pumpGas(13, 2, GasType.REGULAR);
    	afterTime = System.nanoTime();
    	System.out.println(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime));
    	Assert.assertEquals(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 46 * 100, 10.0);

    	// Wait for queues to finish
    	Thread.sleep(3000);
    	
    	beforeTime = System.nanoTime();
    	startThread(30, 2, GasType.SUPER);
    	startThread(20, 2, GasType.SUPER);
    	startThread(20, 2, GasType.SUPER);
    	startThread(10, 2, GasType.REGULAR);
    	startThread(30, 2, GasType.DIESEL);
    	startThread(20, 2, GasType.DIESEL);
    	startThread(10, 2, GasType.DIESEL);
    	startThread(30, 2, GasType.DIESEL);
    	startThread(20, 2, GasType.DIESEL);
    	startThread(10, 2, GasType.DIESEL);
    	Thread.sleep(100);
    	pumpGas(10, 2, GasType.REGULAR); // Wait 10 + 10 (self) Millisecs
    	pumpGas(10, 2, GasType.SUPER); // Wait 20 + 10 - 20 (previous) = 10 + 10 (self) Millisecs
    	pumpGas(10, 2, GasType.DIESEL); // Wait 40 - 40 + 10 (self)
    	afterTime = System.nanoTime();
    	System.out.println(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime));
    	Assert.assertEquals(TimeUnit.NANOSECONDS.toMillis(afterTime - beforeTime), 50 * 100, 10.0);
    	
    	Thread.sleep(3000);
    	
    	// Test if at the end, the expected number of sales and total revenue is availabe
    	
    	Assert.assertEquals(station.getNumberOfSales(), numberOfSales);
    	Assert.assertEquals(station.getRevenue(), totalRevenue, 0.001);

    	// Test if there's not more than 100 liters of Diesel availabe
    	testThreadedNotEnoughGas(100, 2, GasType.DIESEL);
    }

}
