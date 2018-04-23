package project;

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

	String imp;
	if(args.length==0){
	    imp = "./ab_c.wav";
	}else{
	    imp=args[1];
	}

	
	

	
	System.out.println("Reading in impulse file: " + imp);
	double[][] impulse= Audio.readFileDouble(imp);
	final int sampleConvolveLen = sampleConvolveLenNF;


	System.out.println("Using a convolution length of " + sampleConvolveLen);
	
	Convolution cLeft = new Convolution(impulse[0], impulse[0].length + sampleConvolveLen -1);
	Convolution cRight = new Convolution(impulse[1], impulse[0].length + sampleConvolveLen -1);
	

	while (true){
	    
	    final double[][] d = Audio.read();
	    final int len = d[0].length;
	   
	  
	    try{
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
	      writeMutex.release();
	    }catch (InterruptedException ie){
		  ie.printStackTrace();
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

    }


}
