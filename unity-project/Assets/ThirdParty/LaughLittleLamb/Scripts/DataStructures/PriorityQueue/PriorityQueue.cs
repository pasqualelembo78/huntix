using System;
using UnityEngine;

public abstract class PriorityQueue<T>
{
    public struct Element
    {
        public readonly int Priority;
        public readonly T Value;

        public Element(int priority, T value)
        {
            Priority = priority;
            Value = value;
        }

        public void Deconstruct(out int priority, out T value)
        {
	        priority = Priority;
	        value = Value;
        }
    } 
    
    protected Element[] Elements;
    protected int Size;
    public int Count => Size;

    protected PriorityQueue(int size)
    {
        Elements = new Element[size];
    }

    protected int GetLeftChildIndex(int elementIndex) => 2 * elementIndex + 1;
    protected int GetRightChildIndex(int elementIndex) => 2 * elementIndex + 2;
    protected int GetParentIndex(int elementIndex) => (elementIndex - 1) / 2;

    protected bool HasLeftChild(int elementIndex) => GetLeftChildIndex(elementIndex) < Size;
    protected bool HasRightChild(int elementIndex) => GetRightChildIndex(elementIndex) < Size;
    protected bool IsRoot(int elementIndex) => elementIndex == 0;

    protected int GetLeftChildPriority(int elementIndex) => Elements[GetLeftChildIndex(elementIndex)].Priority;
    protected int GetRightChildPriority(int elementIndex) => Elements[GetRightChildIndex(elementIndex)].Priority;
    protected int GetParentPriority(int elementIndex) => Elements[GetParentIndex(elementIndex)].Priority;

    protected void Swap(int firstIndex, int secondIndex) => (Elements[firstIndex], Elements[secondIndex]) = (Elements[secondIndex], Elements[firstIndex]);

    public bool IsEmpty() => Size == 0;

    public Element Peek()
    {
        if (Size == 0) throw new IndexOutOfRangeException();
        return Elements[0];
    }

    public Element Dequeue()
    {
        if (Size == 0) throw new IndexOutOfRangeException();
        var result = Elements[0];
        Elements[0] = Elements[--Size];

        CalculateDown();

        return result;
    }
    
    public void Enqueue(int priority, T value)
    {
        if (Size == Elements.Length)
        {
            Debug.LogWarning($"Expanding array to {Size*2}");
            Array.Resize(ref Elements,Size*2);
            //throw new IndexOutOfRangeException();
        }
        Elements[Size++] = new Element(priority, value);

        CalculateUp();
    }

    protected abstract void CalculateDown();

    protected abstract void CalculateUp();
}





/*
public abstract class IndexedPriorityQueue<T> : PriorityQueue<T>
{
    public struct Element
    {
        public readonly int Priority;
        public readonly T Value;

        public Element(int priority, T value)
        {
            Priority = priority;
            Value = value;
        }
    } 
    
    protected Element[] Elements;
    protected int Size;
    
    
    
    public int Count => Size;

    protected IndexedPriorityQueue(int size)
    {
        Elements = new Element[size];
    }

    protected int GetLeftChildIndex(int elementIndex) => 2 * elementIndex + 1;
    protected int GetRightChildIndex(int elementIndex) => 2 * elementIndex + 2;
    protected int GetParentIndex(int elementIndex) => (elementIndex - 1) / 2;

    protected bool HasLeftChild(int elementIndex) => GetLeftChildIndex(elementIndex) < Size;
    protected bool HasRightChild(int elementIndex) => GetRightChildIndex(elementIndex) < Size;
    protected bool IsRoot(int elementIndex) => elementIndex == 0;

    protected int GetLeftChildPriority(int elementIndex) => Elements[GetLeftChildIndex(elementIndex)].Priority;
    protected int GetRightChildPriority(int elementIndex) => Elements[GetRightChildIndex(elementIndex)].Priority;
    protected int GetParentPriority(int elementIndex) => Elements[GetParentIndex(elementIndex)].Priority;

    protected void Swap(int firstIndex, int secondIndex) => (Elements[firstIndex], Elements[secondIndex]) = (Elements[secondIndex], Elements[firstIndex]);

    public bool IsEmpty() => Size == 0;

    public Element Peek()
    {
        if (Size == 0) throw new IndexOutOfRangeException();
        return Elements[0];
    }

    public Element Dequeue()
    {
        if (Size == 0) throw new IndexOutOfRangeException();
        var result = Elements[0];
        Elements[0] = Elements[--Size];

        CalculateDown();

        return result;
    }
    
    public void Enqueue(int priority, T value)
    {
        if (Size == Elements.Length)
        {
            Debug.LogWarning($"Expanding array to {Size*2}");
            Array.Resize(ref Elements,Size*2);
            //throw new IndexOutOfRangeException();
        }
        Elements[Size++] = new Element(priority, value);

        CalculateUp();
    }

    protected abstract void CalculateDown();

    protected abstract void CalculateUp();
}*/