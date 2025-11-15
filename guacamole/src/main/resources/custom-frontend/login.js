/*
 * Custom login controller for WebGate Guacamole
 * Adds token-based authentication support
 */

(function() {
    'use strict';

    angular.module('login').controller('CustomLoginController', 
        ['$scope', '$injector', '$rootScope', '$location', '$route',
        function CustomLoginController($scope, $injector, $rootScope, $location, $route) {
        
        // Required types
        var Error = $injector.get('Error');
        var Field = $injector.get('Field');

        // Required services
        var authenticationService = $injector.get('authenticationService');
        var requestService        = $injector.get('requestService');

        /**
         * The initial value for all login fields.
         * @constant
         * @type String
         */
        var DEFAULT_FIELD_VALUE = '';

        /**
         * Whether to show token authentication field
         * @type Boolean
         */
        $scope.showTokenField = false;

        /**
         * A description of the error that occurred during login, if any.
         * @type TranslatableMessage
         */
        $scope.loginError = null;

        /**
         * All form values entered by the user, as parameter name/value pairs.
         * @type Object.<String, String>
         */
        $scope.enteredValues = {};

        /**
         * All form fields which have not yet been filled by the user.
         * @type Field[]
         */
        $scope.remainingFields = [];

        /**
         * Whether an authentication attempt has been submitted.
         * @type Boolean
         */
        $scope.submitted = false;

        /**
         * The field that is most relevant to the user.
         * @type Field
         */
        $scope.relevantField = null;

        /**
         * Returns whether a previous login attempt is continuing.
         * @return {Boolean}
         */
        $scope.isContinuation = function isContinuation() {
            for (var name in $scope.values)
                return true;
            return false;
        };

        /**
         * Toggle between token and username/password authentication modes
         */
        $scope.toggleAuthMode = function toggleAuthMode() {
            $scope.showTokenField = !$scope.showTokenField;
            
            // Clear entered values when switching modes
            if ($scope.showTokenField) {
                // Clear username/password fields when switching to token mode
                if ($scope.enteredValues.username) {
                    $scope.enteredValues.username = DEFAULT_FIELD_VALUE;
                }
                if ($scope.enteredValues.password) {
                    $scope.enteredValues.password = DEFAULT_FIELD_VALUE;
                }
            } else {
                // Clear token field when switching to username/password mode
                if ($scope.enteredValues.token) {
                    $scope.enteredValues.token = DEFAULT_FIELD_VALUE;
                }
            }
            
            $scope.loginError = null;
        };

        /**
         * Submits the currently-specified fields to the authentication service
         */
        $scope.login = function login() {
            // Any values from URL parameters
            const urlValues = $location.search();

            // Values from the fields
            const fieldValues = $scope.enteredValues;

            // All the values to be submitted in the auth attempt
            const authParams = {...urlValues, ...fieldValues};

            // If using token authentication, ensure token parameter is included
            if ($scope.showTokenField && authParams.token) {
                // For token authentication, we might need to handle it differently
                // For now, we'll just pass it as a regular parameter
                console.log('Token authentication attempt:', authParams.token);
            }

            authenticationService.authenticate(authParams)['catch'](requestService.IGNORE);
        };

        /**
         * Returns the field most relevant to the user
         * @return {Field}
         */
        var getRelevantField = function getRelevantField() {
            for (var i = 0; i < $scope.remainingFields.length; i++) {
                var field = $scope.remainingFields[i];
                if (!$scope.enteredValues[field.name])
                    return field;
            }
            return null;
        };

        // Ensure provided values are included within entered values
        $scope.$watch('values', function resetEnteredValues(values) {
            angular.extend($scope.enteredValues, values || {});
        });

        // Update field information when form is changed
        $scope.$watch('form', function resetRemainingFields(fields) {
            if (!fields) {
                $scope.remainingFields = [];
                return;
            }

            $scope.remainingFields = fields.filter(function isRemaining(field) {
                return !(field.name in $scope.values);
            });

            angular.forEach($scope.remainingFields, function setDefault(field) {
                if (!$scope.enteredValues[field.name])
                    $scope.enteredValues[field.name] = DEFAULT_FIELD_VALUE;
            });

            $scope.relevantField = getRelevantField();
        });

        // Update UI to reflect in-progress auth status
        $rootScope.$on('guacLoginPending', function loginSuccessful() {
            $scope.submitted = true;
            $scope.loginError = null;
        });

        // Retry route upon success
        $rootScope.$on('guacLogin', function loginSuccessful() {
            $route.reload();
        });

        // Reset upon failure
        $rootScope.$on('guacLoginFailed', function loginFailed(event, parameters, error) {
            $scope.submitted = false;

            if (error.type !== Error.Type.INSUFFICIENT_CREDENTIALS) {
                if (error.type === Error.Type.INVALID_CREDENTIALS) {
                    $scope.loginError = {
                        'key' : 'LOGIN.ERROR_INVALID_LOGIN'
                    };
                } else {
                    $scope.loginError = error.translatableMessage;
                }

                // Reset all remaining fields to default values
                angular.forEach($scope.remainingFields, function clearEnteredValueIfPassword(field) {
                    if (field.type !== Field.Type.USERNAME && field.name in $scope.enteredValues)
                        $scope.enteredValues[field.name] = DEFAULT_FIELD_VALUE;
                });
            }
        });

        // Reset state after authentication and routing have succeeded
        $rootScope.$on('$routeChangeSuccess', function routeChanged() {
            $scope.enteredValues = {};
            $scope.submitted = false;
        });

    }]);

})();