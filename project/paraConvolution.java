package project;

import java.util.*;

import java.util.concurrent.*;
import java.net.*;
import java.io.*;
import org.jtransforms.fft.DoubleFFT_1D;

public class paraConvolution{
    private double[] impulse;
    private DoubleFFT_1D fft;
    private double[] overlap;
    private int fftLen;
    private int lastOverlap;


    private double[] result;
    
    private double[] fftIn;
    private List<double[]> impulses;
    private final int NUM_THREADS;
    private final int lenPerThread; 
    private ExecutorService executor;
    
   
    public paraConvolution(double[] imp, int sampleLen, int num_threads){
	/*calculate the number of impulse samples per thread */
	NUM_THREADS = num_threads;
	lenPerThread =(int) Math.ceil((double)imp.length/NUM_THREADS); /*need ciel to account for last bit */
	//System.out.println("Impulse len is: " + imp.length + ", Using " + num_threads +  " threads, with " + lenPerThread + " impulse samples per thread.");

	/*Calculate the FFTLen for each thread */
	fftLen = sampleLen + lenPerThread -1; //this is per thread 

	
	overlap = new double[fftLen];
	fft = new DoubleFFT_1D(fftLen);
	lastOverlap=0;

	fftIn= new double[fftLen];
	impulses = new ArrayList<double[]>();

	//create array of small impulse ffts
	for(int i =0; i < NUM_THREADS; i++){
	    double[] temp = new double[fftLen];
	    // System.out.println("i=" + i + ", lpt=" + lenPerThread +", implen=" + imp.length);
	    // System.out.println("Copying segment of imp from " + i*lenPerThread + " to " + (i*lenPerThread+ lenPerThread) + " With len " +  (lenPerThread > imp.length-i*lenPerThread ? imp.length-i*lenPerThread : lenPerThread));
		System.arraycopy(imp, i*lenPerThread, temp, 0,
				 (lenPerThread > imp.length-i*lenPerThread ?
				  imp.length-i*lenPerThread : lenPerThread));
	    fft.realForward(temp);
	    impulses.add(temp);
	    
	}
    
	//create thread pool
	executor = Executors.newFixedThreadPool(NUM_THREADS);

	//create return array
	result = new double[lenPerThread*NUM_THREADS + sampleLen];
	
    }


    public double[] paraConvolve(double[] input, int start, int len) throws Exception{
	Arrays.fill(fftIn, 0);
	//      fftIn= new double[fftLen];
	Arrays.fill(overlap,0);
	//	overlap = new double[fftLen];
	lastOverlap = 0;
	//	Arrays.fill(result, 0);
	result = new double[fftLen*NUM_THREADS];
	
	//System.out.println("Start: " + start + ", len: " + len + ", size: " + input.length);
	System.arraycopy(input, start, fftIn, 0, len);
	fft.realForward(fftIn);

	
	List<paraConvolutionWorker> threads = new ArrayList<paraConvolutionWorker>();
	for(int i =0; i < NUM_THREADS; i++){
	    threads.add(new paraConvolutionWorker(impulses.get(i), fftLen, fftIn, fft));
	}
	List<Future<double[]>> results = null;
	results = executor.invokeAll(threads);
	int j = 0;
	int oLen =lenPerThread;
	for(Future<double[]> f : results){
	    double[] temp = f.get();
	    for(int i = 0; i < lastOverlap; i++){
		temp[i]+= overlap[i];
	    }
	   
	    for(int i = oLen; i < fftLen; i++){
		
		overlap[i-oLen] = temp[i];
	    }
	    lastOverlap=fftLen-oLen;

	    System.arraycopy(temp, 0, result, j*lenPerThread, fftLen);
	    j++;
	}
	   return result;
    }
 
}


class paraConvolutionWorker implements Callable<double[]>{

    private double[] impulse;
    private DoubleFFT_1D fft;
    private int fftLen;
    private double[] fftIn;

    //imp should already be in freq domain (i.e. already have a fft run on it)
    public paraConvolutionWorker(double[] imp, int fftLen, double[] in, DoubleFFT_1D dft){
	this.impulse = imp;
	this.fftLen = fftLen;
	fftIn = new double[in.length];
	System.arraycopy(in, 0, fftIn, 0, in.length);
	this.fft = dft;
    }
    
    public double[] call() throws Exception{

	
	
	//special since fftIn[1] is not fftIn[0]'s complex partner
	fftIn[0] = fftIn[0]*impulse[0];
	
	for(int i = 1; i <fftLen/2; i++){
	    double realIn = fftIn[2*i];
	    double imIn = fftIn[2*i+1];
	    double realImp = impulse[2*i];
	    double imImp = impulse[2*i+1];
        
	    double temp = realIn*realImp - imIn*imImp;
	    fftIn[2*i+1] = realIn*imImp + imIn*realImp;
	    fftIn[2*i] = temp;
	    
	}

	// if the len is odd, the complex part of last sample is in fftIn[1]
	if(fftIn.length%2 ==1){
	    
	    double temp = fftIn[fftIn.length-1] *impulse[fftIn.length-1]-
		fftIn[1]*impulse[1];
	    
	    fftIn[1]= fftIn[fftIn.length-1]*impulse[1] +
		fftIn[1]*impulse[fftIn.length-1];
	    
	    fftIn[fftIn.length-1]=temp;
	}else{
	    //if it is even, last real part is in fft[1]
	    fftIn[1] *=impulse[1];
	}


	fft.realInverse(fftIn, true);
	return fftIn;
    }



}
