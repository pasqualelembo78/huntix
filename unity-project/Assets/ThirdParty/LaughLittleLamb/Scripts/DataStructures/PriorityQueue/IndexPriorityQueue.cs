using System;
using System.Collections.Generic;

[System.Serializable]
public class IndexPriorityQueue
{
    public readonly int MaxSize;

    public int Size { get; private set; }
    
    private readonly int[] _priority;
    private readonly int[] _positionToIndex;
    private readonly int[] _indexToPosition;
    
    private bool[] _contains;

    public IReadOnlyList<int> Priority => _priority;
    public IReadOnlyList<int> PositionToIndex => _positionToIndex;
    public IReadOnlyList<int> IndexToPosition => _indexToPosition;

    public readonly bool _revertOrder;

    public IndexPriorityQueue(int capacity, bool revertOrder = false)
    {
        MaxSize = capacity;
        _revertOrder = revertOrder;
        Size = 0;
        _priority = new int[capacity];
        _positionToIndex = new int[capacity];
        _indexToPosition = new int[capacity];
        _contains = new bool[capacity];
        for (var index = 0; index < capacity; index++)
        {
            _priority[index] = MaxValue();
            _positionToIndex[index] = -1;
            _indexToPosition[index] = -1;
        }
    }

    public int GetPriority(int index) => _priority[index];

    public void Enqueue(int index, int priority)
    {
        if (_revertOrder) priority *= -1;
        
        if (_contains[index])
            throw new Exception("Value already in queue");
        
        _contains[index] = true;

        _positionToIndex[Size] = index;
        _indexToPosition[index] = Size;
        _priority[index] = priority;
        Size++;
        Up(Size-1);
    }

    public bool IsEmpty() => Size == 0;

    public int PeekPriority() => _priority[_positionToIndex[0]];
    public int PeekIndex() => _positionToIndex[0];

    public int DequeueIndex()
    {
        if (Size == 0) 
            throw new Exception("Empty");
        
        int index = _positionToIndex[0];
        _contains[index] = false;

        Swap(0, --Size);
        
        int i = _positionToIndex[Size];
        _positionToIndex[Size] = -1;
        _indexToPosition[i] = -1;
        _priority[i] = MaxValue();
        Down(0);

        return index;
    }


    public void Update(int index, int priority)
    {
        if (_revertOrder) priority *= -1;

        if(!_contains[index])
            throw new Exception($"Index:{index} not in queue");

        int oldPriority = _priority[index];
        if(oldPriority == priority)
            return;
        
        _priority[index] = priority;
        
        if (priority < oldPriority)
            Up(_indexToPosition[index]);
        else
            Down(_indexToPosition[index]);
    }

    private void Up(int index)
    {
        while (HasParent(index) && Compare(index, GetParentIndex(index)))
        {
            int parentIndex = GetParentIndex(index);
            Swap(index,parentIndex);
            index = parentIndex;
        }
    }

    private void Down(int iPos)
    {
        while (HasLeftChild(iPos))
        {
            int smallestChildIPosition = GetLeftChildIndexPos(iPos);

            bool hasRightChild = HasRightChild(iPos);
            
            if (hasRightChild && Compare(GetRightChildIndexPos(iPos), smallestChildIPosition))
            {
                smallestChildIPosition = GetRightChildIndexPos(iPos);
            }

            if (Compare(iPos,smallestChildIPosition))
                break;
            
            Swap(iPos,smallestChildIPosition);
            iPos = smallestChildIPosition;
        }
    }

    private bool Compare(int i, int j)
    {
        try
        {
            return _priority[_positionToIndex[i]] < _priority[_positionToIndex[j]];
        }
        catch (Exception e)
        {
            throw new Exception($"i:{i} j:{j} {_priority.Length} Vi:{_positionToIndex[i]} Vj:{_positionToIndex[j]}");
        }
    } 

    private void Swap(int i, int j)
    {
        int iIndex = _positionToIndex[i];
        int jIndex = _positionToIndex[j];
        //Debug.Log($"SWAP [{_indexToPosition[iIndex]}]:{iIndex} > [{_indexToPosition[jIndex]}]:{jIndex}");
        
        (_indexToPosition[iIndex], _indexToPosition[jIndex]) = (_indexToPosition[jIndex], _indexToPosition[iIndex]);
        (_positionToIndex[i], _positionToIndex[j]) = (_positionToIndex[j], _positionToIndex[i]);
        
    }
    
    public int GetParentIndex(int i) => (int)Math.Ceiling((i - 2f) / 2);
    public int GetLeftChildIndexPos(int i) => i * 2 + 1;
    public int GetRightChildIndexPos(int i) => i * 2 + 2;

    public bool HasLeftChild(int i) => GetLeftChildIndexPos(i) < Size;
    public bool HasRightChild(int i) => GetRightChildIndexPos(i) < Size;
    public bool HasParent(int i) => GetParentIndex(i) >= 0;


    private int MaxValue() => int.MaxValue;
    private int MinValue() => int.MinValue;

    public void EnqueueOrUpdate(int index, int priority)
    {
        if (_revertOrder) priority *= -1;

        if(_contains[index])
            Update(index,priority);
        else
            Enqueue(index,priority);
    }

    public bool Contains(int index) => _contains[index];

    public (int index, int priority) Dequeue()
    {
        var priority = PeekPriority();
        var index = DequeueIndex();
        return (index, priority);
    }
}
