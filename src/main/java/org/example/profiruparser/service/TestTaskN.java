package org.example.profiruparser.service;

import java.util.*;

/**
 * Решение тестового задания по алгоритмам
 * Задача 1: Анализ Insertion-Sort
 * Задача 2: Реализация вставки в связанный список
 * Задача 3: Визуализация дерева из скобочной записи
 */

public class TestTaskN {

    /**
     * ЗАДАЧА 1: Анализ алгоритма Insertion-Sort
     * Алгоритм: Сортировка вставками
     * Сложность: O(n²) в худшем случае, O(n) в лучшем (уже отсортированный массив)
     * Память: O(1) без учета визуализации, O(n) с визуализацией
     */
    public static void analyzeInsertionSort(int[] arr) {
        System.out.println("=== ЗАДАЧА 1: АНАЛИЗ INSERTION-SORT ===");
        System.out.println("Исходный массив: " + Arrays.toString(arr));
        System.out.println();

        int n = arr.length;
        int[][] steps = new int[n][n]; /* Для хранения состояний на каждом шаге*/
        int[] workingArray = arr.clone(); /* Рабочая копия массива*/

        /* Сохраняем начальное состояние*/

        System.arraycopy(workingArray, 0, steps[0], 0, n);

        /* Основной алгоритм Insertion-Sort*/

        for (int j = 1; j < n; j++) {
            int key = workingArray[j];
            int i = j - 1;

            /* Сохраняем состояние перед обработкой j-го элемента*/

            System.arraycopy(workingArray, 0, steps[j], 0, n);

            /* Сдвигаем элементы больше key вправо*/

            while (i >= 0 && workingArray[i] > key) {
                workingArray[i + 1] = workingArray[i];
                i--;
            }
            workingArray[i + 1] = key; /* Вставляем key на правильную позицию*/
        }

        /* Вывод таблицы с шагами алгоритма*/

        printAnalysisTable(steps, n);
    }

    /**
     * Вспомогательный метод для вывода таблицы анализа
     */
    private static void printAnalysisTable(int[][] steps, int n) {
        System.out.println("Таблица выполнения алгоритма:");
        System.out.print("j\\A\t");
        for (int i = 0; i < n; i++) {
            System.out.print("[" + (i + 1) + "]\t");
        }
        System.out.println();

        for (int j = 0; j < n; j++) {
            System.out.print((j + 1) + "\t");
            for (int i = 0; i < n; i++) {
                System.out.print(steps[j][i] + "\t");
            }
            System.out.println();
        }
    }

    /**
     * ЗАДАЧА 2: Реализация связанного списка с операцией вставки в голову
     * Сложность вставки: O(1) - константное время
     * Память: O(1) - только перенаправление указателей
     */

    /* Класс элемента двусвязного списка*/

    static final class ListNode {
        public int key;
        public ListNode next;
        public ListNode prev;

        public ListNode(int key) {
            this.key = key;
            this.next = null;
            this.prev = null;
        }
    }

    /* Класс двусвязного списка*/

    static final class DoublyLinkedList {
        private ListNode head;

        public DoublyLinkedList() {
            this.head = null;
        }

        /**
         * List-Insert(L, x) - вставка элемента x в голову списка L
         * Сложность: O(1)
         * @param x - элемент для вставки (не должен быть null)
         */
        public void insertHead(ListNode x) {
            if (x == null) {
                throw new IllegalArgumentException("Element cannot be null");
            }

            /* Устанавливаем указатели нового элемента*/

            x.next = head;
            x.prev = null;

            /* Обновляем указатель предыдущей головы (если список не пуст)*/

            if (head != null) {
                head.prev = x;
            }

            /* Новый элемент становится головой списка*/

            head = x;
        }

        /**
         * Вспомогательный метод для вывода списка
         */
        public void printList() {
            ListNode current = head;
            StringBuilder sb = new StringBuilder();
            while (current != null) {
                sb.append(current.key);
                if (current.next != null) {
                    sb.append(" ↔ ");
                }
                current = current.next;
            }
            System.out.println("Список: " + (sb.length() > 0 ? sb.toString() : "пуст"));
        }

        public ListNode getHead() {
            return head;
        }
    }

    /**
     * Демонстрация работы со связанным списком
     */
    public static void demonstrateLinkedList() {
        System.out.println("\n=== ЗАДАЧА 2: СВЯЗАННЫЙ СПИСОК ===");

        DoublyLinkedList list = new DoublyLinkedList();

        /* Создаем тестовые элементы*/

        ListNode node1 = new ListNode(1);
        ListNode node2 = new ListNode(2);
        ListNode node3 = new ListNode(3);
        ListNode node4 = new ListNode(4);

        System.out.println("Вставка в пустой список:");
        list.insertHead(node1);
        list.printList();

        System.out.println("Вставка в непустой список:");
        list.insertHead(node2);
        list.insertHead(node3);
        list.insertHead(node4);
        list.printList();

        /* Проверка корректности указателей*/

        System.out.println("Проверка указателей:");
        ListNode current = list.getHead();
        while (current != null) {
            String prevInfo = current.prev != null ? String.valueOf(current.prev.key) : "NIL";
            String nextInfo = current.next != null ? String.valueOf(current.next.key) : "NIL";
            System.out.println("Узел " + current.key + ": prev=" + prevInfo + ", next=" + nextInfo);
            current = current.next;
        }
    }

    /**
     * ЗАДАЧА 3: Визуализация дерева из скобочной записи
     */
    public static void visualizeTreeFromInput(String input) {
        System.out.println("\n=== ЗАДАЧА 3: ВИЗУАЛИЗАЦИЯ ДЕРЕВА ===");
        System.out.println("Входные данные: " + input);

        try {
            List<String> tokens = tokenizeInput(input);
            TreeNode root = buildTreeStructure(tokens);

            System.out.println("Визуализация дерева:");
            if (root != null) {
                printTreeVisualization(root, "", true);
            } else {
                System.out.println("Дерево пустое");
            }
        } catch (Exception e) {
            System.out.println("Ошибка парсинга: " + e.getMessage());
        }
    }

    /* Класс узла дерева*/

    static final class TreeNode {
        String value;
        List<TreeNode> children;

        TreeNode(String value) {
            this.value = value;
            this.children = new ArrayList<>();
        }
    }

    /* Токенизация входной строки*/

    private static List<String> tokenizeInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();

        for (char c : input.toCharArray()) {
            if (c == '(' || c == ')') {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString().trim());
                    currentToken.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else if (Character.isWhitespace(c)) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString().trim());
                    currentToken.setLength(0);
                }
            } else {
                currentToken.append(c);
            }
        }

        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString().trim());
        }

        return tokens;
    }

    /* Построение дерева из токенов */

    private static TreeNode buildTreeStructure(List<String> tokens) {
        if (tokens.isEmpty()) return null;

        Stack<TreeNode> stack = new Stack<>();
        TreeNode root = null;

        for (String token : tokens) {
            if (token.equals("(")) {

                /* Начало новой группы children*/

                continue;
            } else if (token.equals(")")) {

                /* Конец группы children*/

                if (stack.size() > 1) {
                    stack.pop();
                }
            } else {

                /* Значение узла*/

                TreeNode node = new TreeNode(token);

                if (stack.isEmpty()) {
                    root = node;
                } else {
                    stack.peek().children.add(node);
                }

                stack.push(node);
            }
        }

        return root;
    }

    /* Рекурсивная визуализация дерева */

    private static void printTreeVisualization(TreeNode node, String prefix, boolean isLast) {
        if (node == null) return;

        System.out.print(prefix);
        System.out.print(isLast ? "└── " : "├── ");
        System.out.println(node.value);

        String newPrefix = prefix + (isLast ? "    " : "│   ");
        for (int i = 0; i < node.children.size(); i++) {
            printTreeVisualization(node.children.get(i), newPrefix, i == node.children.size() - 1);
        }
    }

    /**
     * Главный метод демонстрации
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        /* Задача 1: Анализ Insertion-Sort*/

        int[] testArray = {3, 2, 1, 5};
        analyzeInsertionSort(testArray);

        /* Задача 2: Связанный список*/

        demonstrateLinkedList();

        /* Задача 3: Визуализация дерева*/

        System.out.println("\nВведите дерево в формате (1 (2 (4 5 6 (7) 108 (9)) 3))");
        System.out.print("Или нажмите Enter для примера по умолчанию: ");

        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            input = "(1 (2 (4 5 6 (7) 108 (9)) 3))";
        }

        visualizeTreeFromInput(input);

        scanner.close();

        System.out.println("\n=== ВЫПОЛНЕНИЕ ЗАВЕРШЕНО ===");
    }

}