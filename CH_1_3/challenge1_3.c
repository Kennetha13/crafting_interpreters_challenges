#include <stdio.h>
#include <stdlib.h>
#include <string.h>

//Defining a node, all variables are pointers
typedef struct Node {
    char *value;
    struct Node *next;
    struct Node *prev;
} Node;


void insert(Node **head, const char *value) {

    Node *newNode = malloc(sizeof(Node));

    newNode->value = malloc(strlen(value) + 1);
    strcpy(newNode->value, value);

    newNode->prev = NULL;
    newNode->next = *head;

    //setting the node before the one being added's previous paramter to the the newNode
    //If First Node then (Node *head = NULL) creating ( NULL<-prev newNode next->NULL) then newNode becomes head
    if (*head != NULL) {
        (*head)->prev = newNode;
    }

    //make the newNode head (NULL<-prev newNode(head) next <-> prev Node next -> NULL )
    *head = newNode;
}

Node *find(Node *head, const char *value) {

    Node *current = head;

    while (current != NULL) {

        if (strcmp(current->value, value) == 0) {
            return current;
        }

        current = current->next;
    }

    return NULL;
}


void delete(Node **head, const char *value) {

    Node *node = find(*head, value);

    if (node == NULL) {
        return;
    }

    if (node->prev != NULL) {
        node->prev->next = node->next;
    } else {
        *head = node->next;
    }

    if (node->next != NULL) {
        node->next->prev = node->prev;
    }

    free(node->value);
    free(node);
}


void printList(Node *head) {

    Node *current = head;

    while (current != NULL) {
        printf("%s\n", current->value);
        current = current->next;
    }
}


int main() {

    Node *head = NULL;

    insert(&head, "apple");
    insert(&head, "banana");
    insert(&head, "orange");

    printf("List:\n");
    printList(head);

    Node *result = find(head, "banana");

    if (result != NULL) {
        printf("\nFound: %s\n", result->value);
    }

    delete(&head, "banana");

    printf("\nAfter deleting banana:\n");
    printList(head);

    delete(&head, "apple");
    delete(&head, "orange");

    return 0;
}