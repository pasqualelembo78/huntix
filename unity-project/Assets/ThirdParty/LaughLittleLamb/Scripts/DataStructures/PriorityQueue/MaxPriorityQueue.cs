public class MaxPriorityQueue<T> : PriorityQueue<T>
{
    public MaxPriorityQueue(int size) : base(size)
    {
    }
    
    protected override void CalculateDown()
    {
        int index = 0;
        while (HasLeftChild(index))
        {
            var smallerIndex = GetLeftChildIndex(index);
            if (HasRightChild(index) && GetRightChildPriority(index) > GetLeftChildPriority(index))
            {
                smallerIndex = GetRightChildIndex(index);
            }

            if (Elements[smallerIndex].Priority <= Elements[index].Priority)
            {
                break;
            }

            Swap(smallerIndex, index);
            index = smallerIndex;
        }
    }

    protected override void CalculateUp()
    {
        var index = Size - 1;
        while (!IsRoot(index) && Elements[index].Priority > GetParentPriority(index))
        {
            var parentIndex = GetParentIndex(index);
            Swap(parentIndex, index);
            index = parentIndex;
        }
    }
}