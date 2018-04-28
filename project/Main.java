package project;
import java.util.*;
import java.util.concurrent.*;
public class Main{

    private static double[] convolvedLeft = null;
    private static double[] convolvedRight=null;
    public static Semaphore writeMutex = new Semaphore ( 2 );
    public static void setCL(double[] i){
	convolvedLeft = i;
    }
      public static void setCR(double[] i){
	convolvedRight = i;
    }

    public static void runFromFile(String[] args){
	if(args.length != 0 && args.length != 3 && args.length!= 1){
	    throw new IllegalArgumentException("Invalid Arguments! Expected <inputFile.wav> <impulseFile.wav>");
	}
	Audio.init();
	String input;
	String imp;
	int sampleConvolveLenNF= 10000;//impulse[0].length;
	if(args.length==0 || args.length==1){
	    input = "./PreludeInDm.WAV";
	    imp = "./ab_c.wav";
	}else{
	    input = args[1];
	    imp=args[2];
	}
	if(args.length > 0){
	    sampleConvolveLenNF=Integer.parseInt(args[0]);
	}
	
	System.out.println("Reading in input file: " + input);
	double[][] d = Audio.readFileDouble(input);
	
	System.out.println("Reading in impulse file: " + imp);
	double[][] impulse= Audio.readFileDouble(imp);
	final int sampleConvolveLen = sampleConvolveLenNF;


	System.out.println("Using a convolution length of " + sampleConvolveLen);
	
	Convolution cLeft = new Convolution(impulse[0], impulse[0].length + sampleConvolveLen -1);
	Convolution cRight = new Convolution(impulse[1], impulse[0].length + sampleConvolveLen -1);
	

	for(int i = 0; i < d[0].length/sampleConvolveLen; i++){
	  
	    final int index = i;
	    try{

	
		
		writeMutex.acquire();
		new Thread()
		{
		    public void run() {
			setCL(cLeft.convolve(d[0], index*sampleConvolveLen, sampleConvolveLen));
			writeMutex.release();
		    }
		}.start();
		writeMutex.acquire();
		new Thread()
		{
		    public void run() {
			setCR(cRight.convolve(d[1], index*sampleConvolveLen, sampleConvolveLen));
			writeMutex.release();
		    }
		}.start();

		writeMutex.acquire();
		writeMutex.acquire();
		Audio.write(convolvedLeft,convolvedRight, sampleConvolveLen);
		writeMutex.release();
		writeMutex.release();
	    }catch (InterruptedException ie){
		ie.printStackTrace();
	    }
	}
	    
	
	if(d.length%sampleConvolveLen!=0){
	    convolvedLeft = cLeft.convolve(d[0], ((int)d.length/sampleConvolveLen)*sampleConvolveLen,d.length%sampleConvolveLen);
	     convolvedRight = cRight.convolve(d[1], ((int)d.length/sampleConvolveLen)*sampleConvolveLen,d.length%sampleConvolveLen);
	    Audio.write(convolvedLeft, convolvedRight,d.length%sampleConvolveLen);
	}

	Audio.write(cLeft.getOverlap(), cRight.getOverlap(), cLeft.getOverlapSize() < cRight.getOverlapSize()?
	      cLeft.getOverlapSize():cRight.getOverlapSize() );
	Audio.flush();
	
	Audio.disconnect();

    }



    
    public static void runLive(String[] args){
	Audio.init();

	int sampleConvolveLenNF= 5000;//impulse[0].length;
	final int NUM_THREADS = 8; //should be datasize/sampleLen *2 (for stereo)
	String imp;
	if(args.length==0){
	    imp = "./ab_c.wav";
	}else{
	    imp=args[0];
	}

	
	

	
	System.out.println("Reading in impulse file: " + imp);
	double[][] impulse= Audio.readFileDouble(imp);
	final int sampleConvolveLen = sampleConvolveLenNF;


	System.out.println("Using a convolution length of " + sampleConvolveLen);

	//create an array of all convolving objects
	Convolution convolvers[] = new Convolution[NUM_THREADS];
	for(int i = 0; i < NUM_THREADS; i+=2){
	    convolvers[i] = new Convolution(impulse[0], impulse[0].length + sampleConvolveLen -1);
	    convolvers[i+1] = new Convolution(impulse[1], impulse[0].length + sampleConvolveLen -1);
	}
	

	//create our executor
	ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
	double[][] leftOver = null;
	    
	while (true){
	    
	    double[][] d = Audio.read();
	     int len = d[0].length;
	    List<double[]> lefts = new ArrayList<double[]>();
	    List<double[]> rights = new ArrayList<double[]>();

	    //add in leftovers from last time
	    if(leftOver != null){
		double[][]d1 = new double[2][len+leftOver[0].length];
	        d1[0] = Arrays.copyOf(leftOver[0], len+leftOver[0].length);
		d1[1] = Arrays.copyOf(leftOver[1], len+leftOver[1].length);
		System.arraycopy(d[0],0,d1[0], leftOver[0].length, len);
		System.arraycopy(d[1],0,d1[1], leftOver[1].length, len);
		d = d1;
		len = d[0].length; //reset since it was updated
	    }

	    
	    try{
		
		List<convolver> threads = new ArrayList<convolver>();
		List<Future<double[]>> results = null;
		int numThreadsNeeded = ((int)(len/sampleConvolveLen))*2;//*2 for stereo

		//save left over for next time (things not even multiple of sampleConvolveLen
		if(len%sampleConvolveLen != 0){
		    leftOver = new double[2][len%sampleConvolveLen];
		    System.arraycopy(d[0],sampleConvolveLen*(numThreadsNeeded/2),
				     leftOver[0], 0, len%sampleConvolveLen);
		    System.arraycopy(d[1],sampleConvolveLen*(numThreadsNeeded/2),
				     leftOver[1], 0, len%sampleConvolveLen);
		    
	
		}else{
		    leftOver = null;
		}
		/*create all of our threads */
		for(int i = 0; i < numThreadsNeeded; i+=2){
		    threads.add(new convolver(d[0], convolvers[i],sampleConvolveLen*(i/2), sampleConvolveLen));
		    threads.add(new convolver(d[1], convolvers[i+1],sampleConvolveLen*(i/2), sampleConvolveLen));
		}
		/*run our convolution in parallel */
		results = executor.invokeAll(threads);
		/*get the results from all of the runs */
		int i = 0;
		for(Future<double[]> f : results){
		    if(i%2==0){
			lefts.add(f.get());
		    }else{
			rights.add(f.get());
		    }
		    i++;
		}
		/*write it to the sound card */
		Audio.write(lefts, rights, sampleConvolveLen);

		/*
	    writeMutex.acquire();
	     new Thread()
	     {
		 public void run() {
		     setCL(cLeft.convolve(d[0], 0, len));
		     writeMutex.release();
		 }
	     }.start();
	     writeMutex.acquire();
	      new Thread()
	     {
		 public void run() {
		     setCR(cRight.convolve(d[1], 0, len));
		      writeMutex.release();
		 }
	     }.start();

	      writeMutex.acquire();
	      writeMutex.acquire();
	      Audio.write(convolvedLeft,convolvedRight, sampleConvolveLen);
	      writeMutex.release();
	      writeMutex.release();*/
	    }catch (InterruptedException ie ){
		ie.printStackTrace();
	    }catch(ExecutionException ex){
		ex.printStackTrace();
	    }
	    
	}
	    
	
	/*
	Audio.flush();
	Audio.disconnect();
	*/
    }
    public static void main (String [] args){
	//	runFromFile(args);
	runLive(args);
	Audio.echo();

    }


}

class convolver implements Callable<double[]>{
    double data[];
    Convolution c;
    int start;
    int len;
    public convolver(double[] data, Convolution c, int start, int len){
	this.data = data;
	this.c = c;
	this.start = start;
	this.len = len;
    }
    public double[] call() throws Exception{
	return c.convolve(data, start, len);
    }

}
