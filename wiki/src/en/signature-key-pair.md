# Signature Key Pair

## Overview
This document introduces the key pair used for digital signatures. OpenSSL will be used to manipulate keys in this document.

The authentication server will [digitally sign](yggdrasil-server-technical-specification.md#serialization-of-profile-information) the profile properties in responses to the following requests:
 * [Server verifies client](yggdrasil-server-technical-specification.md#server-verifies-client)
 * [Query profile attributes](yggdrasil-server-technical-specification.md#query-profile-attributes) (only required when `unsigned=false`)

The authentication server publishes its public key via [API metadata](yggdrasil-server-technical-specification.md#api-metadata-retrieval) so that authlib-injector can obtain it.

Note: The authentication server should avoid changing keys. If multiple server instances are used for load balancing, they should all use the same key.

## Generating and Handling Key Pairs
The following OpenSSL invocations use stdin and stdout for input and output.
To use files, you can use the arguments `-in <file>` and `-out <file>`.

### Generating a Private Key
The key algorithm is RSA, with a recommended length of 4096 bits.

```
openssl genrsa 4096
```

The generated private key will be output to stdout.

### Generating a Public Key from a Private Key
```
openssl rsa -pubout
```

The private key is read from stdin, and the public key is output to stdout.
