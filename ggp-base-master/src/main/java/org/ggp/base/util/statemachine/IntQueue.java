package org.ggp.base.util.statemachine;

public class IntQueue {
	private int size;
	private int[] arr;
	private int curr = 0;
	private int next = 0;
	public int num_queued = 0;

	public IntQueue(int s) {
		this.size = s;
		arr = new int[s];
	}

	public void add(int val) {
		arr[curr] = val;
		++curr;
		++num_queued;
	}

	public int remove() {
		--num_queued;
		return arr[next++];
	}

	public boolean isEmpty() {
		return (num_queued == 0);
	}

	public void clear() {
		num_queued = 0;
		curr = 0;
		next = 0;
	}
}
