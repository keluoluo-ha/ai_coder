package com.hhk.aicoder.service.impl;

import org.apache.commons.lang3.StringUtils;

import java.util.*;

import static cn.hutool.core.util.ArrayUtil.swap;

class Solution {
    public class TreeNode {
        int val;
        TreeNode left;
        TreeNode right;

        TreeNode() {
        }

        TreeNode(int val) {
            this.val = val;
        }

        TreeNode(int val, TreeNode left, TreeNode right) {
            this.val = val;
            this.left = left;
            this.right = right;
        }
    }

    //俩数之和
    public int[] twoSum(int[] nums, int target) {
        int[] result = new int[2];
        HashMap<Integer, Integer> numsMap = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int temp = target - nums[i];
            if (!numsMap.containsKey(temp)) {
                numsMap.put(temp, i);
            } else {
                result[0] = numsMap.get(temp);
                result[1] = i;
            }

        }
        return result;

    }

    /**
     * 给定一个二叉树的根节点 root ，返回它的 中序 遍历。
     *
     * @param root
     * @return
     */

    public List<Integer> inorderTraversal(TreeNode root) {
        List<Integer> res = new ArrayList<>();
        dfs(root, res);
        return res;
    }

    public void dfs(TreeNode root, List<Integer> res) {
        if (root == null) {
            return;
        }
        dfs(root.left, res);
        res.add(root.val);
        dfs(root.right, res);
    }

//三树之和
    public List<List<Integer>> threeSum(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        if (nums == null || nums.length < 3) {
            return res;
        }
        Arrays.sort(nums);
        int n = nums.length;

        // 固定第一个数 nums[i]
        for (int i = 0; i < n; i++) {
            // 对i去重，避免固定一样的开头数字
            if (i > 0 && nums[i] == nums[i - 1]) {
                continue;
            }
            int left = i + 1;
            int right = n - 1;
            while (left < right) {
                int sum = nums[i] + nums[left] + nums[right];
                if (sum == 0) {
                    res.add(Arrays.asList(nums[i], nums[left], nums[right]));
                    // left去重
                    while (left < right && nums[left] == nums[left + 1]) {
                        left++;
                    }
                    // right去重
                    while (left < right && nums[right] == nums[right - 1]) {
                        right--;
                    }
                    left++;
                    right--;
                } else if (sum < 0) {
                    // 和偏小，左指针右移增大总和
                    left++;
                } else {
                    // 和偏大，右指针左移减小总和
                    right--;
                }
            }

        }
        return res;
    }
    //子数组和为k
    public int subarraySum(int[] nums, int k) {
        int res=0;
        HashMap<Integer, Integer> numTarget = new HashMap<>();
        //前缀和
        int length = nums.length;
        int preSum = 0;

        //key是子数组的和 value是子数组的个数
        numTarget.put(0,1);

        for (int i=0;i<length;i++)
        {
            preSum=preSum+nums[i];
            int temp = preSum - k;
           if(numTarget.containsKey(temp)){
               res+=numTarget.get(temp);
           }
            numTarget.put(preSum,numTarget.getOrDefault(preSum,0)+1);

        }

        return res;
    }

    //爬楼梯
    public int climbStairs(int n) {
        // 一次性处理n=1、n=2两种基础情况
        if (n <= 2) {
            return n;
        }
        //初始化数组
        int[] res=new int[n+1];
        res[1]=1;
        res[2]=2;
        for (int i=3;i<=n;i++){
            res[i]=res[i-1]+res[i-2];
        }
        return res[n];
    }


    public static class ListNode {
     int val;
      ListNode next;
      ListNode() {}
      ListNode(int val) { this.val = val; }
      ListNode(int val, ListNode next) { this.val = val; this.next = next; }
  }
    //合并俩个有序链表
    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        ListNode res = new ListNode();
        ListNode cur=res;
        while (list1!=null&&list2!=null){
            if(list1.val<=list2.val){
                cur.next=list1;
                list1=list1.next;
            }else {
                cur.next=list2;
                list2=list2.next;
            }
            cur=cur.next;
        }
        if(list2==null){
            cur.next=list1;
        }
        if(list1==null){
            cur.next=list2;
        }
        return res.next;
    }
    //编辑距离
    public int minDistance(String word1, String word2) {
        //res[i][j]表示word1前i个字符转换成word2前j个字符所使用的最少操作数
            int[][] res=new int[word1.length()+1][word2.length()+1];
            res[0][0]=0;

            for(int i=1;i<=word1.length();i++){
                res[i][0]=i;
            }
            for (int i=1;i<=word2.length();i++){
                res[0][i]=i;
            }

            for (int i=1;i<=word1.length();i++){
                for (int j=1;j<=word2.length();j++){
                    if(word1.charAt(i-1)==word2.charAt(j-1)){
                        res[i][j]=res[i-1][j-1];
                    }else {
                        res[i][j] = Math.min( Math.min(res[i-1][j-1]+1, res[i-1][j]+1), res[i][j-1]+1 );
                    }
                }
            }

            return res[word1.length()][word2.length()];
    }

    //最大子数组和
    public int maxSubArray(int[] nums) {
        if(nums.length==1){
            return nums[0];
        }
        //必须以 nums [i] 结尾的连续子数组的最大和
        int[] res=new int[nums.length];
        int maxval=nums[0];
        res[0]=nums[0];
        for(int i=1;i<nums.length;i++){
            maxval=Math.max(maxval,Math.max(res[i-1]+nums[i],nums[i]));
            res[i] = Math.max(res[i-1]+nums[i], nums[i]);
        }

        return res[nums.length-1];

    }
    //对称二叉树

    public boolean isSymmetric(TreeNode root) {
        if(root == null){
            return true;
        }
        return isTrue(root.left,root.right);
    }


    public boolean isTrue(TreeNode rootleft,TreeNode rootright) {
        if(rootleft==null&&rootright==null) {
            return true;
        }
        if(rootleft==null||rootright==null){
            return false;
        }

        if(rootleft.val!= rootright.val){
            return false;
        }

        return isTrue(rootleft.left,rootright.right)&&isTrue(rootleft.right,rootright.left);

    }


    //合并区间
    public int[][] merge(int[][] intervals) {

        //按区间排序
        Arrays.sort(intervals,((a,b)->a[0]-b[0]));
        List<int []> res = new ArrayList<>();
        for(int[] cur:intervals){
            if(res.isEmpty()||cur[0]>res.get(res.size()-1)[1]){
                res.add(cur);
            }else {
                int[] last = res.get(res.size() - 1);
                last[1]=Math.max(last[1],cur[1]);
            }
        }
        return res.toArray(new int[res.size()][]);


    }

    //合并二叉树
    public TreeNode mergeTrees(TreeNode root1, TreeNode root2) {
        if(root1 != null && root2 != null){
            root1.val += root2.val;
        }else if(root1 == null && root2 != null){
            root1 = new TreeNode(root2.val);
        }else if(root1 != null && root2 == null){
            // root1保留，无需任何操作
        }else{
            // 两者都为空
            return null;
        }
        root1.left=mergeTrees(root1.left,root2.left);
        root1.right=mergeTrees(root1.right,root2.right);
        return root1;
    }

    //层序遍历
    List<List<Integer>> res = new ArrayList<>();
    public List<List<Integer>> levelOrder(TreeNode root) {
      res = new ArrayList<>();
        treehelper(root);
        return res;
    }

    public void treehelper(TreeNode root) {
        
    }
    //颜色分类
    public void sortColors(int[] nums) {

        int left=0;
        int right=nums.length-1;
        int i=0;
        while (i<=right){
            if(nums[i]==0){
                int temp = nums[left];
                nums[left] = nums[i];
                nums[i] = temp;
                left++;
                i++;
            }
            if(nums[i]==2){
                // 换到右边，i不前进，新交换过来的值需要再次检查
                int temp = nums[right];
                nums[right] = nums[i];
                nums[i] = temp;
                right--;
            }else {
                i++;
            }


        }

    }
    String[] arr = {null,null,"abc","def","ghi","jkl","mno","pqrs","tuv","wxyz"};
//电话号码组合
    public List<String> letterCombinations(String digits) {
        List<String> letterRes = new ArrayList<>();
        // 关键边界：null 或者空字符串，直接返回空列表
        if (digits == null || digits.isEmpty()) {
            return letterRes;
        }

        Combinations(digits,0,"",letterRes);
        return letterRes;
    }
    public void Combinations(String digits,int k,String tempres,  List<String> letterRes) {
        if(k>=digits.length()){
            letterRes.add(tempres);
            return ;
        }
        int num =  digits.charAt(k)-'0';
        String temp = arr[num];
        for(int i=0;i<temp.length();i++){
            char c = temp.charAt(i);
            Combinations(digits,k+1,tempres + c,letterRes);
        }

    }
//找到所有数组中消失的数字
        public List<Integer> findDisappearedNumbers(int[] nums) {
            List<Integer> res = new ArrayList<>();
            int n = nums.length;
            for(int i = 0; i < n; i++){
                // 获取原始数值（防止已经被取负）
                int val = Math.abs(nums[i]);
                int idx = val - 1;
                if(nums[idx] > 0){
                    nums[idx] = -nums[idx];
                }
            }
            // 正数下标+1 就是消失的数字
            for(int i = 0; i < n; i++){
                if(nums[i] > 0){
                    res.add(i + 1);
                }
            }
            return res;

        }
        //有效的括号
    public boolean isValid(String s) {
        if(s==null|| StringUtils.isEmpty(s)){
            return false;
        }
        //如果是奇数肯定为空
        if(s.length()%2==1){
            return false;
        }
        Stack<Character> stack = new Stack<>();

        for(int i=0;i<s.length();i++){
            char c = s.charAt(i);
            if(c=='('||c=='{'||c=='['){
                    stack.push(c);
            }else{
                if(stack.isEmpty()) {
                    return false;
                }else {
                    char top = stack.peek();
                    if( (c == ')' && top == '(')
                            || (c == ']' && top == '[')
                            || (c == '}' && top == '{') ){
                        stack.pop();
                    }else{
                        // 不匹配，直接失败
                        return false;
                    }
                }
            }
        }
        return stack.isEmpty();
    }
    //二叉树的直径
    int max=0;
    public int diameterOfBinaryTree(TreeNode root) {
        dfs(root);
        return max;
    }
    public int dfs(TreeNode treeNode){
        if(treeNode==null) return 0;
        int leftdepth = dfs(treeNode.left);
        int rightdepth = dfs(treeNode.right);
        max = Math.max(max, leftdepth + rightdepth);
        return Math.max(leftdepth,rightdepth)+1;

    }

    //多数元素
    public int majorityElement(int[] nums) {
        int n = nums.length / 2;
        Map<Integer, Integer> map = new HashMap<>();
        for(int i=0;i<nums.length;i++){
            map.put(nums[i],map.getOrDefault(nums[i],0)+1);
        }
        // 遍历map找到出现次数 > n 的元素
        for(Map.Entry<Integer, Integer> entry : map.entrySet()){
            if(entry.getValue() > n){
                return entry.getKey();
            }
        }
        // 题目保证一定存在多数元素，不会走到这里
        return -1;
    }

    //环形链表
    public boolean hasCycle(ListNode head) {
        ListNode fast=head;
        ListNode slow =head;
        while (fast!=null&&fast.next!=null){
            slow=slow.next;
            fast=fast.next.next;
            if(slow==fast){
                return true;
            }
        }
        return false;

    }

    //排序链表
    public ListNode sortList(ListNode head) {
        if (head==null||head.next==null){
            return head;
        }
        ListNode fast=head.next;
        ListNode slow =head;
        while (fast!=null&&fast.next!=null){
            slow=slow.next;
            fast = fast.next.next;
        }
        ListNode rightHead = slow.next;
        slow.next=null;

        ListNode left=sortList(head);
        ListNode right = sortList(rightHead);


        return merge(left,right);
    }

    private ListNode merge(ListNode left, ListNode right) {
        ListNode node = new ListNode(0);
        ListNode curr=node;
        while (left!=null&&right!=null){
            if(left.val<right.val){
                curr.next=left;
                left=left.next;
            }else {
                curr.next=right;
                right=right.next;
            }
            curr=curr.next;
        }
        curr.next = left == null ? right : left;
        return node.next;
    }

    //旋转图像
    public void rotate(int[][] matrix) {
        int n=matrix.length;
        // 1. 转置
        for(int i = 0; i < n; i++){
            for(int j = i + 1; j < n; j++){
                int temp = matrix[i][j];
                matrix[i][j] = matrix[j][i];
                matrix[j][i] = temp;
            }
        }
        // 2. 每行翻转
        for(int i = 0; i < n; i++){
            int left = 0;
            int right = n - 1;
            while(left < right){
                int temp = matrix[i][left];
                matrix[i][left] = matrix[i][right];
                matrix[i][right] = temp;
                left++;
                right--;
            }
        }


    }

    //括号生成
//    public List<String> generateParenthesis(int n) {
//
//        //二维dp dp[i][j]表示n=i的括号列表
//        List<List<String>> dp = new ArrayList<>();
//        List<String> init0 = new ArrayList<>();
//        init0.add("");
//        if(n==0) return init0;
//        dp.add(init0);
//        for(int i=1;i<=n;i++){
//            List<String> curlist = new ArrayList<>();
//            for(int a=0;a<i;a++){
//                int b=i-1-a;
//                List<String> alist = dp.get(a);
//                List<String> blist = dp.get(b);
//                for (String strA : alist) {
//                    for (String strB : blist) {
//                        // 拼接公式："(" + A + ")" + B
//                        curlist.add("(" + strA + ")" + strB);
//                    }
//                }
//            }
//            // 把算好的当前 i 的结果存入 dp
//            dp.add(curlist);
//
//            }
//
//        return dp.get(n-1);
//    }

    public List<String> generateParenthesis(int n) {
        List<String> list = new ArrayList<>();
        dfs(list,n,0,0,new StringBuilder());
        return list;
    }
    public void dfs(List<String> list ,int num,int left,int right,StringBuilder cur){
        if(cur.length()==2*num){
            list.add(cur.toString());
            return;
        }

        if(left<num){
            cur.append('(');
            dfs(list,num,left+1,right,cur);
            cur.deleteCharAt(cur.length()-1);
        }
        if(right<left){
            cur.append(')');
            dfs(list,num,left,right+1,cur);
            cur.deleteCharAt(cur.length()-1);
        }

    }
    //只出现一次数字
    public int singleNumber(int[] nums) {
        int res = 0;
        for (int num : nums) {
            res ^= num;
        }
        return res;
    }
    //买卖股票的最佳时机含冷冻期
    public int maxProfit(int[] prices) {
        //初始化 1. 持有股票 2. 不持有，不在冷冻期 3. 不持有，处于冷冻期
        int n=prices.length;
        int[][] profit = new int[prices.length][3];
        profit[0][0]=-prices[0];
        profit[0][1]=Integer.MIN_VALUE;
        profit[0][2]=Integer.MIN_VALUE;
        int res=0;
        for(int i=1;i<prices.length;i++){
            profit[i][0]=Math.max(profit[i-1][0],profit[i-1][1]-prices[i]);
            profit[i][1]=Math.max(profit[i-1][2],profit[i-1][1]);
            profit[i][2]=profit[i-1][0]+prices[i];
        }



        return Math.max(Math.max(profit[n-1][1], profit[n-1][2]),0);
    }
    //无重复字符的最长字串  s = "abcabcbb"
    public int lengthOfLongestSubstring(String s) {
        if(s==null) return 0;
        if(s.length()==0) return 0;
        int res=0;
        int fast=0;
        int slow=0;
        int n = s.length();
        HashMap<Character, Integer> map = new HashMap<>();
        while(fast<n){
            if(map.containsKey(s.charAt(fast))){
                slow=Math.max(slow,map.get(s.charAt(fast))+1);
                res = Math.max(res, fast-slow+1); // 只重复时更新
            }
            map.put(s.charAt(fast),fast);
            fast++;
        }
        // 循环结束必须额外算一遍！！处理没有重复的字符串
        return Math.max(res, fast-slow);

    }
    //最长回文字符串
    public String longestPalindrome(String s) {
        if(s.length()==1|| s.isEmpty()) return s;
        int n = s.length();
        //初始化 dp[i][j]表示从i到j最长回文子串
        int[][] dp = new int[n][n];
        for(int i=0;i<n;i++){
            dp[i][i]=1;
        }
        int maxLen=1;
        int start=0;

        for(int i=n-1;i>=0;i--){
            for(int j=i+1;j<n;j++){
                if(s.charAt(i)==s.charAt(j)){
                    if(j - i ==1){
                        dp[i][j] =2;
                    }else{
                        if(dp[i+1][j-1]>0){
                            dp[i][j]= dp[i+1][j-1]+2;
                        }else{
                            dp[i][j]=0;
                        }
                    }
                    if(dp[i][j]>maxLen){
                        maxLen=dp[i][j];
                        start=i;
                    }
                }else {
                    dp[i][j]=0;
                }

            }
        }

        return s.substring(start,start+maxLen);

//
//        for(int i = 0; i < n; i++){
//            // 在这里调用expand两次
//            // 奇数中心
//            String str1 = expand(s,i,i);
//            // 偶数中心
//            String str2 = expand(s,i,i+1);
//            String cur="";
//            // 选出str1 str2较长的赋值给current
//            if (str1.length()>str2.length()){
//                cur=str1;
//            }else {
//                cur=str2;
//            }
//            // 如果current长度大于maxStr，更新maxStr
//            if (cur.length()>maxStr.length()){
//                maxStr=cur;
//            }
//        }
    }
    private String expand(String s, int left, int right){
        // while循环，满足条件就向外扩散
        while( left>=0&&right<s.length()&&s.charAt(left)==s.charAt(right) ){
            left--;
            right++;
        }
        //循环结束截取并返回回文子串
        return s.substring(left+1, right);
    }

    //最大乘积子数组
    public int maxProduct(int[] nums) {
        int res=nums[0];
        int n = nums.length;
        //- `dpMax[i]`：**以 i 位置结尾**的子数组最大乘积
        //- `dpMin[i]`：**以 i 位置结尾**的子数组最小乘积（负数，负负得正）
        int[] dpMax = new int[n];
        int[] dpMin = new int[n];
        dpMax[0]=nums[0];
        dpMin[0]=nums[0];
        for (int i=1;i<n;i++){
            dpMin[i]=Math.min(dpMin[i-1]*nums[i],Math.min(dpMax[i-1]*nums[i],nums[i]));
            dpMax[i]=Math.max(dpMax[i-1]*nums[i],Math.max(dpMin[i-1]*nums[i],nums[i]));
            res=Math.max(res,dpMax[i]);
        }

        return res;
        //dp[i][j]表示从i到j的连续乘积
//        int[][] dp = new int[n][n];
//
//        for(int i=0;i<n;i++){
//            dp[i][i]=nums[i];
//            res = Math.max(res, dp[i][i]);
//            for (int j=i+1;j<n;j++){
//                dp[i][j] = dp[i][j - 1] * nums[j];
//                res=Math.max(res,dp[i][j]);
//            }
//        }

    }

        //数组中第k个最大的元素
        public int findKthLargest(int[] nums, int k) {
            Arrays.sort(nums);
            int n = nums.length;
            return nums[n - k];
        }

        //翻转二叉树
    public TreeNode invertTree(TreeNode root) {
        if(root==null) return null;
        TreeNode temp = root.right;
        root.right=root.left;
        root.left=temp;
        invertTree(root.left);
        invertTree(root.right);

        return root;
    }

    //跳跃游戏
    public boolean canJump(int[] nums) {
        int maxReach=0+nums[0];

        for(int i=0;i<nums.length;i++){
            if(maxReach<i) return false;
            maxReach=Math.max(maxReach,i+nums[i]);
            if(maxReach>=nums.length-1){
                return true;
            }
        }
        return false;
    }
    //回文链表
    public boolean isPalindrome(ListNode head) {
        if(head.next==null||head==null) return true;
        ListNode slow=head;
        ListNode fast=head;
        //找到中点
        while (fast!=null&&fast.next!=null){
            fast=fast.next.next;
            slow=slow.next;
        }
        //反转链表
        ListNode p2 = reverse(slow.next);
        ListNode p1 = head;
        while (p2!=null){
            if(p1.val != p2.val){
                return false;
            }
            p1 = p1.next;
            p2 = p2.next;
        }
        return true;
    }
    private ListNode reverse(ListNode node) {
        ListNode pre = null;
        ListNode cur = node;
        while (cur!=null){
            ListNode temp = cur.next;
            cur.next=pre;
            pre=cur;
            cur=temp;
        }
        return pre;
    }

    //不同路径
    public int uniquePaths(int m, int n) {
        //dp[i][j]表示到达 i,j有多少条路径
        int[][] dp = new int[m][n];
        for (int i=0;i<m;i++){
            dp[i][0]=1;
        }
        for (int j=0;j<n;j++){
            dp[0][j]=1;
        }
        for (int i=1;i<m;i++){
            for(int j=1;j<n;j++){
                    dp[i][j]=dp[i-1][j]+dp[i][j-1];
            }
        }
        return dp[m-1][n-1];

    }
    //组合总和
    public List<List<Integer>> combinationSum(int[] candidates, int target) {
        List<List<Integer>> res = new ArrayList<>();
        combination(candidates,target,0,res,new ArrayList<>());
        return res;
    }
    //引入start 防止重复
    public void combination(int[] candidates, int target,int start,List<List<Integer>> res,List<Integer> temp) {
        if(target==0){
            res.add(new ArrayList<>(temp));
            return;
        }
        if(target<0){
            return;
        }
        for (int i=start;i<candidates.length;i++){
            temp.add(candidates[i]);
            combination(candidates,target-candidates[i],i,res,temp);
            temp.removeLast();
        }
    }
    //相交链表
    public ListNode getIntersectionNode(ListNode headA, ListNode headB) {
        ListNode p1 = headA;
        ListNode p2 = headB;
        while (p1 != p2) {
            // p1走完A，走B；p2走完B，走A
            p1 = p1 == null ? headB : p1.next;
            p2 = p2 == null ? headA : p2.next;
        }
        return p1;
    }
    //字符串解码
    public String decodeString(String s) {
        Stack<String> stack = new Stack<>();
        String res="";
        for (int i=0;i<s.length();i++){
            char c = s.charAt(i);
            if(Character.isDigit(c)){
                StringBuilder builder = new StringBuilder();
                while (i<s.length()&&Character.isDigit(s.charAt(i))){
                    builder.append(s.charAt(i));
                    i++;
                }
                stack.push(builder.toString());
                i--;
            }else if(s.charAt(i)=='['){
              stack.push(res);
              res="";
            }else if(s.charAt(i)==']'){
                int k = Integer.parseInt(stack.pop());
                String pop = stack.pop();
                StringBuilder builder = new StringBuilder();
                for (int j=0;j<k;j++){
                    builder.append(pop);
                }
                res=builder.toString();
            }else {
                res+=c;
            }
        }
        return res;
    }
    //课程表 有向图
    public boolean canFinish(int numCourses, int[][] prerequisites) {
        List<List<Integer>> adj = new ArrayList<>();
        for(int i=0;i<numCourses;i++){
            adj.add(new ArrayList<>());
        }
        for(int[] p : prerequisites){
            int a = p[0];
            int b = p[1];
            adj.get(b).add(a); // b -> a
        }
        //- `0`：未访问
        //- `1`：当前递归栈中（碰到这个代表出现环）
        //- `2`：全部子节点处理完毕
        int[] state = new int[numCourses];
        for(int i=0;i<numCourses;i++){
            if(state[i]==0){
                boolean has = dfs(i, adj, state);
                if(has) return false;
            }
        }
        return true;

    }
    public boolean dfs(int cur,List<List<Integer>> adj,  int[] state){
       state[cur]=1;
        List<Integer> list = adj.get(cur);
        for(int i=0;i<list.size();i++){
            Integer next = list.get(i);
            if(state[next]==1) return true;
            if(state[next]==0) {
                boolean has = dfs(next, adj, state);
                if(has) return true;
            }
        }
        state[cur]=2;
        return false;
    }
    //子集
    public List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        dfs(nums,0,res ,new ArrayList<>());
        return res;
    }
    public void dfs(int[] nums,int num,List<List<Integer>> res,List<Integer> temp){

       res.add(new ArrayList<>(temp));

        for (int i=num;i<nums.length;i++){
            temp.add(nums[i]);
            dfs(nums,i+1,res,temp);
            temp.remove(temp.size()-1);

        }
    }

    //全排列
    public List<List<Integer>> permute(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> temp=new ArrayList<>();
        boolean[] used=new boolean[nums.length];
        dfs(nums,used,res,temp);
        return res;
    }
    public void dfs(int[] nums ,boolean[] used, List<List<Integer>> res, List<Integer> temp){
        if(temp.size()==nums.length)
        {
            res.add(new ArrayList<>(temp));
            return;
        }
        for (int i=0;i<nums.length;i++){
            if(used[i]) {
                continue;
            }
            used[i]=true;
            temp.add(nums[i]);
            dfs(nums,used,res,temp);
            temp.remove(temp.size()-1);
            used[i]=false;
        }
    }
    //任务调度器
    public int leastInterval(char[] tasks, int n) {
        int[] taskCount = new int[26];
        int max=0;
        int res=0;
        for (char task:tasks){
            int i = task - 'A';
            taskCount[i]+=1;
        }
        for (int i=0;i<26;i++){
            max=Math.max(max,taskCount[i]);
        }
        int maxCountNum=0;
        for (int i=0;i<26;i++){
          if(taskCount[i]==max) maxCountNum++;
        }
        int candidate1 = (max - 1) * (n + 1) + maxCountNum;

        return Math.max(candidate1,tasks.length);
    }

    //下一个排列
    public void nextPermutation(int[] nums) {


        int i = nums.length - 2;
        while (i >= 0 && nums[i] >= nums[i+1]) {
            i--;
        }
        if(i<0){
            //整个数组降序，反转全部，结束
            reverse(nums,0,nums.length-1);
            return;
        }
        for (int j = nums.length - 1; j >= 0; j--) {
            if (nums[j] > nums[i]) {
                swap(nums, i, j);
                break; // 找到就交换，立刻跳出j循环
            }
        }
        reverse(nums,i+1,nums.length-1);

    }
    // 反转 [l, r] 区间
    public static void reverse(int[] arr, int l, int r) {
        int left = l;
        int right = r;
        while (left < right) {
            int temp = arr[left];
            arr[left] = arr[right];
            arr[right] = temp;
            left++;
            right--;
        }
    }
    private void swap(int[] nums, int a, int b){
        int temp = nums[a];
        nums[a] = nums[b];
        nums[b] = temp;
    }


    //岛屿数量
    public int numIslands(char[][] grid) {
        if(grid == null || grid.length == 0){
            return 0;
        }

        int res=0;
        boolean[][]map= new boolean[grid.length][grid[0].length];
        int[][] direction={{1,0},{-1,0},{0,-1},{0,1}};
        for(int i=0;i<grid.length;i++){
            for (int j=0;j<grid[0].length;j++){
                if(grid[i][j]=='1'&&!map[i][j]){
                    res++;
                    dfs(grid,map,direction,i,j);
                }
            }
        }
        return res;
    }
    public void dfs(char[][] grid, boolean[][]map ,int[][] direction,int x,int y){
            map[x][y]=true;
            for (int i = 0; i <4; i++){
                int nx = x + direction[i][0];
                int ny = y + direction[i][1];
                if(nx<grid.length&&nx>=0&&ny<grid[0].length&&ny>=0&&grid[nx][ny]=='1'&&map[nx][ny]==false){
                    dfs(grid,map,direction,nx,ny);

                }
            }
    }
    //打劫家舍
    public int rob(int[] nums) {
        if(nums == null || nums.length == 0){
            return 0;
        }

        //记录状态 0是不偷 1是偷
        int[][] dp = new int[nums.length][2];
        dp[0][0]=0;
        dp[0][1]=nums[0];
        int res=Math.max(dp[0][0],dp[0][1]);
        for (int i=1;i<nums.length;i++){
            dp[i][0]=Math.max(dp[i-1][0],dp[i-1][1]);
            dp[i][1]=dp[i-1][0]+nums[i];
            res=Math.max(dp[i][0], Math.max(dp[i][1],res));
        }
        return res;
    }

    //分割等和子集
    public boolean canPartition(int[] nums) {
        int sum=0;
        for(int num : nums){
            sum += num;
        }
        // 总和奇数，不可能平分
        if(sum % 2 != 0){
            return false;
        }
        int target = sum / 2;
        boolean[] dp = new boolean[target + 1];
        dp[0]=true;
        for(int num : nums){
            // 01背包：倒序遍历，防止重复选取同一个元素
            for(int j = target; j >= num; j--){
                dp[j] = dp[j] || dp[j - num];
            }
        }
        return dp[target];
    }


    //零钱兑换
    public int coinChange(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        int INF=amount+1;
        for(int i = 1; i <= amount; i++){
            dp[i] = INF;
        }
        dp[0] = 0; //金额0，0枚硬币
        for(int coin : coins){
            //完全背包：正序遍历，可以重复选当前硬币
            for(int j = coin; j <= amount; j++){
                dp[j] = Math.min(dp[j], dp[j - coin] + 1);
            }
        }
        return dp[amount] == INF ? -1 : dp[amount];

    }

    //汉明距离
    public int hammingDistance(int x, int y) {
        // 异或：相同为0，不同为1，结果中1的个数就是汉明距离
        int xor = x ^ y;
        int count = 0;
        while (xor != 0) {
            // 消去最右侧的1
            xor = xor & (xor - 1);
            count++;
        }
        return count;
    }

    //字母异位次分词
    public List<List<String>> groupAnagrams(String[] strs) {
        HashMap<String, List<String>> hashmap = new HashMap<>();
            for(String str:strs){
                char[] charArray = str.toCharArray();
                Arrays.sort(charArray);
                String res = new String(charArray);
                hashmap.getOrDefault(res,new ArrayList<>()).add(str);
            }
        Collection<List<String>> values = hashmap.values();
            return new ArrayList<>(values);
    }


    //删除链表的倒数第n个节点
    public ListNode removeNthFromEnd(ListNode head, int n) {
        ListNode dummy = new ListNode(0);
        dummy.next = head;

        // 1. 求链表长度
        int len = 0;
        ListNode cur = head;
        while (cur != null) {
            len++;
            cur = cur.next;
        }

        // 2. 倒数第 n 个 = 正数第 (len - n + 1) 个，走到它的前驱（从 dummy 走 len - n 步）
        cur = dummy;
        for (int i = 0; i < len - n; i++) {
            cur = cur.next;
        }

        // 3. 跳过待删节点
        cur.next = cur.next.next;
        return dummy.next;
    }
    //盛水最多的容器
    public int maxArea(int[] height) {
        int res=0;
        int left =0;
        int right=height.length-1;
        while (left<right){
            int h = Math.min(height[left], height[right]);
             res =Math.max(res,h * (right - left));
             if(height [left] < height [right]){
                 left++;
             }else {
                 right--;
             }
        }
        return res;
    }
    //单词搜索
    public boolean exist(char[][] board, String word) {
        boolean res=false;
        int [][] dir={{0,1},{0,-1},{1,0},{-1,0}};
        for(int i=0;i<board.length;i++){
            for(int j=0;j<board[0].length;j++){
                if(board[i][j]==word.charAt(0)){
                    if(dfs( board, word,dir,i,j,0))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    public boolean dfs(char[][] grid, String word,int [][] direction,int x,int y,int index){
        if(grid[x][y] != word.charAt(index)){
            return false;
        }
        if(index == word.length()-1){
            return true;
        }
        char temp = grid[x][y];
        grid[x][y]='#';

        for (int i = 0; i <4; i++){
            int nx = x + direction[i][0];
            int ny = y + direction[i][1];
            if(nx<grid.length&&nx>=0&&ny<grid[0].length&&ny>=0&&grid[nx][ny]==word.charAt(index+1)){

                boolean dfs = dfs(grid, word, direction, nx, ny,index+1);
                if(dfs == true){
                    // 一旦找到，直接return true，向上传递结果！
                    grid[x][y] = temp;
                    return true;
                }
            }
        }
        grid[x][y] = temp;
             return false;
    }

}














